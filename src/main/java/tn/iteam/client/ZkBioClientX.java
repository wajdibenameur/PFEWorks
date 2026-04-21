package tn.iteam.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tn.iteam.exception.IntegrationResponseException;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ZkBioClientX {

    private static final String SOURCE = "ZKBIO";
    private static final String SOURCE_LABEL = "ZKBio";
    private static final String RESILIENCE_NAME = "zkbioApi";
    private static final String DEVICE_LIST_ENDPOINT = "/api/v1/device/list";
    private static final String ATTENDANCE_LOG_ENDPOINT = "/api/v1/attendance/log";
    private static final String EVENT_ENDPOINT = "/api/v1/event/push";
    private static final String USER_LIST_ENDPOINT = "/api/v1/user/list";
    private static final String STATUS_ENDPOINT = "/api/v1/status";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${zkbio.url}")
    private String baseUrl;

    @Value("${zkbio.token:}")
    private String apiToken;

    public ZkBioClientX(
            @Qualifier("zkbioUnsafeTlsWebClientForInternalUseOnly") WebClient webClient,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getDevicesFallback")
    public JsonNode getDevices() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("page", 1);
        payload.put("size", 100);
        return postForList(DEVICE_LIST_ENDPOINT, payload);
    }

    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getStatusFallback")
    public JsonNode getStatus() {
        return callGet(STATUS_ENDPOINT);
    }

    public JsonNode getAttendanceLogs() {
        long endTime = System.currentTimeMillis() / 1000;
        long startTime = endTime - 86400;
        return getAttendanceLogs(startTime, endTime);
    }

    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getAttendanceLogsFallback")
    public JsonNode getAttendanceLogs(long startTime, long endTime) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("start_time", startTime);
        payload.put("end_time", endTime);
        payload.put("page", 1);
        payload.put("size", 500);
        return postForList(ATTENDANCE_LOG_ENDPOINT, payload);
    }

    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getAlertsFallback")
    public JsonNode getAlerts() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("page", 1);
        payload.put("size", 100);
        return postForList(EVENT_ENDPOINT, payload);
    }

    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getUsersFallback")
    public JsonNode getUsers() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("page", 1);
        payload.put("size", 100);
        return postForList(USER_LIST_ENDPOINT, payload);
    }

    public URI getBaseUri() {
        try {
            return baseUrl == null || baseUrl.isBlank() ? null : URI.create(baseUrl);
        } catch (Exception ex) {
            log.warn("Invalid ZKBio base URL '{}': {}", baseUrl, ex.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unused")
    private JsonNode getDevicesFallback(Throwable throwable) {
        throw mapCircuitBreakerException("devices API", throwable);
    }

    @SuppressWarnings("unused")
    private JsonNode getStatusFallback(Throwable throwable) {
        throw mapCircuitBreakerException("status API", throwable);
    }

    @SuppressWarnings("unused")
    private JsonNode getAlertsFallback(Throwable throwable) {
        throw mapCircuitBreakerException("alerts API", throwable);
    }

    @SuppressWarnings("unused")
    private JsonNode getUsersFallback(Throwable throwable) {
        throw mapCircuitBreakerException("users API", throwable);
    }

    @SuppressWarnings("unused")
    private JsonNode getAttendanceLogsFallback(long startTime, long endTime, Throwable throwable) {
        throw mapCircuitBreakerException("attendance API", throwable);
    }

    private RuntimeException mapCircuitBreakerException(String apiTarget, Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            log.warn("ZKBio circuit breaker OPEN on {}: {}", apiTarget, throwable.getMessage());
            return new IntegrationUnavailableException(
                    SOURCE,
                    "Circuit breaker open for " + SOURCE_LABEL + " " + apiTarget,
                    throwable
            );
        }

        if (throwable instanceof IntegrationUnavailableException e) {
            return e;
        }

        if (throwable instanceof IntegrationTimeoutException e) {
            return e;
        }

        if (throwable instanceof IntegrationResponseException e) {
            return e;
        }

        return new IntegrationUnavailableException(
                SOURCE,
                SOURCE_LABEL + " unavailable on " + apiTarget,
                throwable
        );
    }

    private JsonNode postForList(String endpoint, Map<String, Object> payload) {
        return extractListPayload(callPost(endpoint, payload));
    }

    private JsonNode callGet(String endpoint) {
        return webClient.get()
                .uri(baseUrl + endpoint)
                .headers(headers -> headers.addAll(createHeaders()))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new IntegrationUnavailableException(
                                        SOURCE,
                                        "ZKBio error: " + body
                                )))
                )
                .bodyToMono(String.class)
                .timeout(REQUEST_TIMEOUT)
                .switchIfEmpty(Mono.just(""))
                .map(this::parseBodyUnchecked)
                .blockOptional()
                .orElseGet(() -> objectMapper.createObjectNode());
    }

    private JsonNode callPost(String endpoint, Map<String, Object> payload) {
        return webClient.post()
                .uri(baseUrl + endpoint)
                .headers(headers -> headers.addAll(createHeaders()))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new IntegrationUnavailableException(
                                        SOURCE,
                                        "ZKBio error: " + body
                                )))
                )
                .bodyToMono(String.class)
                .timeout(REQUEST_TIMEOUT)
                .switchIfEmpty(Mono.just(""))
                .map(this::parseBodyUnchecked)
                .blockOptional()
                .orElseGet(() -> objectMapper.createObjectNode());
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiToken != null && !apiToken.isBlank()) {
            headers.setBearerAuth(apiToken);
        }
        return headers;
    }

    private JsonNode parseBodyUnchecked(String responseBody) {
        try {
            if (responseBody == null || responseBody.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new IntegrationResponseException(SOURCE, "Failed to parse ZKBio response", exception);
        }
    }

    private JsonNode extractListPayload(JsonNode root) {
        if (root == null || root.isNull()) {
            return objectMapper.createArrayNode();
        }
        if (root.has("data") && root.get("data").has("list")) {
            return root.get("data").get("list");
        }
        if (root.has("data") && root.get("data").isArray()) {
            return root.get("data");
        }
        return root;
    }
}
