package tn.iteam.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import tn.iteam.cache.IntegrationCacheService;
import tn.iteam.exception.IntegrationResponseException;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;
import tn.iteam.service.SourceAvailabilityService;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ZkBioClient {

    private static final String DEVICE_LIST_ENDPOINT = "/api/v1/device/list";
    private static final String ATTENDANCE_LOG_ENDPOINT = "/api/v1/attendance/log";
    private static final String EVENT_ENDPOINT = "/api/v1/event/push";
    private static final String USER_LIST_ENDPOINT = "/api/v1/user/list";
    private static final String STATUS_ENDPOINT = "/api/v1/status";
    private static final String SOURCE = "ZKBIO";
    private static final String DEVICES_SNAPSHOT_KEY = "business-devices";
    private static final String STATUS_SNAPSHOT_KEY = "business-status";
    private static final String ALERTS_SNAPSHOT_KEY = "business-alerts";
    private static final String USERS_SNAPSHOT_KEY = "business-users";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @Qualifier("zkbioUnsafeTlsWebClientForInternalUseOnly")
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final IntegrationCacheService integrationCacheService;
    private final SourceAvailabilityService availabilityService;

    @Value("${zkbio.url}")
    private String baseUrl;

    @Value("${zkbio.token:}")
    private String apiToken;

    public ZkBioClient(
            @Qualifier("zkbioUnsafeTlsWebClientForInternalUseOnly") WebClient webClient,
            ObjectMapper objectMapper,
            IntegrationCacheService integrationCacheService,
            SourceAvailabilityService availabilityService
    ) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.integrationCacheService = integrationCacheService;
        this.availabilityService = availabilityService;
    }

    public JsonNode getDevices() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("page", 1);
        payload.put("size", 100);
        return postForList(DEVICE_LIST_ENDPOINT, payload, DEVICES_SNAPSHOT_KEY);
    }

    public JsonNode getStatus() {
        try {
            JsonNode payload = callGet(STATUS_ENDPOINT);
            integrationCacheService.saveSnapshot(SOURCE, STATUS_SNAPSHOT_KEY, payload);
            availabilityService.markAvailable(SOURCE);
            return payload;
        } catch (Exception ex) {
            log.warn("ZKBio status unavailable: {}", ex.getMessage());
            return integrationCacheService.getSnapshot(SOURCE, STATUS_SNAPSHOT_KEY, JsonNode.class)
                    .map(snapshot -> {
                        availabilityService.markDegraded(SOURCE, ex.getMessage());
                        return snapshot;
                    })
                    .orElseGet(() -> {
                        availabilityService.markUnavailable(SOURCE, ex.getMessage());
                        return objectMapper.createObjectNode().put("status", "offline");
                    });
        }
    }

    public JsonNode getAttendanceLogs() {
        long endTime = System.currentTimeMillis() / 1000;
        long startTime = endTime - 86400;
        return getAttendanceLogs(startTime, endTime);
    }

    public JsonNode getAttendanceLogs(long startTime, long endTime) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("start_time", startTime);
        payload.put("end_time", endTime);
        payload.put("page", 1);
        payload.put("size", 500);
        return postForList(
                ATTENDANCE_LOG_ENDPOINT,
                payload,
                "attendance-" + startTime + "-" + endTime
        );
    }

    public JsonNode getAlerts() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("page", 1);
        payload.put("size", 100);

        try {
            return postForList(EVENT_ENDPOINT, payload, ALERTS_SNAPSHOT_KEY);
        } catch (Exception ex) {
            log.warn("Error fetching ZKBio alerts, falling back to attendance logs: {}", ex.getMessage());
            return getAttendanceLogs();
        }
    }

    public JsonNode getUsers() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("page", 1);
        payload.put("size", 100);
        return postForList(USER_LIST_ENDPOINT, payload, USERS_SNAPSHOT_KEY);
    }

    public URI getBaseUri() {
        try {
            return baseUrl == null || baseUrl.isBlank() ? null : URI.create(baseUrl);
        } catch (Exception ex) {
            log.warn("Invalid ZKBio base URL '{}': {}", baseUrl, ex.getMessage());
            return null;
        }
    }

    private JsonNode postForList(String endpoint, Map<String, Object> payload, String snapshotKey) {
        try {
            JsonNode result = extractListPayload(callPost(endpoint, payload));
            integrationCacheService.saveSnapshot(SOURCE, snapshotKey, result);
            availabilityService.markAvailable(SOURCE);
            return result;
        } catch (Exception ex) {
            log.warn("ZKBio endpoint {} unavailable: {}", endpoint, ex.getMessage());
            return integrationCacheService.getSnapshot(SOURCE, snapshotKey, JsonNode.class)
                    .map(snapshot -> {
                        availabilityService.markDegraded(SOURCE, ex.getMessage());
                        return snapshot;
                    })
                    .orElseGet(() -> {
                        availabilityService.markUnavailable(SOURCE, ex.getMessage());
                        return objectMapper.createArrayNode();
                    });
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiToken != null && !apiToken.isEmpty()) {
            headers.setBearerAuth(apiToken);
        }
        return headers;
    }

    private JsonNode callGet(String endpoint) throws Exception {
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
                                        "ZkBio error: " + body
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
                                        "ZkBio error: " + body
                                )))
                )
                .bodyToMono(String.class)
                .timeout(REQUEST_TIMEOUT)
                .switchIfEmpty(Mono.just(""))
                .map(this::parseBodyUnchecked)
                .blockOptional()
                .orElseGet(() -> objectMapper.createObjectNode());
    }

    private JsonNode parseBody(String responseBody) throws Exception {
        if (responseBody == null || responseBody.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(responseBody);
    }

    private JsonNode parseBodyUnchecked(String responseBody) {
        try {
            return parseBody(responseBody);
        } catch (Exception exception) {
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
