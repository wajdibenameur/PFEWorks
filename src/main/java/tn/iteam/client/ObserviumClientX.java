package tn.iteam.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Mono;
import tn.iteam.domain.ApiResponse;
import tn.iteam.exception.IntegrationResponseException;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;
import tn.iteam.util.IntegrationClientSupport;

import java.time.Duration;
import java.util.Locale;

@Slf4j
@Component
public class ObserviumClientX {

    private static final String SOURCE = "OBSERVIUM";
    private static final String SOURCE_LABEL = "Observium";
    private static final String RESILIENCE_NAME = "observiumApi";
    private static final String DEVICES_ENDPOINT = "/api/v0/devices";
    private static final String ALERTS_ENDPOINT = "/api/v0/alerts";
    private static final String X_AUTH_TOKEN = "X-Auth-Token";
    private static final String DEVICES_SUCCESS_MESSAGE = "Devices fetched successfully";
    private static final String ALERTS_SUCCESS_MESSAGE = "Alerts fetched successfully";
    private static final String EMPTY_RESPONSE_PREFIX = "Empty response from Observium: ";
    private static final String HTML_RESPONSE_TEMPLATE =
            "Observium returned HTML instead of JSON on %s (likely wrong URL or token authentication)";
    private static final String NULL_JSON_ROOT_TEMPLATE = "Observium returned a null JSON root for %s";
    private static final String MISSING_FIELD_TEMPLATE = "Observium response missing '%s' field for %s (status=%s)";
    private static final String NOT_ARRAY_TEMPLATE = "Observium response field '%s' is not an array for %s";
    private static final String MISSING_FIELD_LOG_TEMPLATE =
            "Observium response for {} is missing expected field '{}' (status={})";
    private static final String NOT_ARRAY_LOG_TEMPLATE =
            "Observium response field '{}' for {} is not an array (type={})";
    private static final String HTTP_ERROR_LOG_TEMPLATE = "Observium HTTP error on {}: {}";
    private static final String TIMEOUT_LOG_TEMPLATE = "Observium timeout/unreachable on {}: {}";
    private static final String INVALID_JSON_LOG_TEMPLATE = "Observium invalid JSON on {}: {}";
    private static final String TRANSPORT_ERROR_LOG_TEMPLATE = "Observium transport error on {}: {}";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String token;

    public ObserviumClientX(
            WebClient webClient,
            ObjectMapper objectMapper,
            @Value("${observium.url}") String baseUrl,
            @Value("${observium.token}") String token
    ) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.token = token;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isBlank()) {
            headers.set(X_AUTH_TOKEN, token.trim());
        }
        return headers;
    }

    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getDevicesFallback")
    public ApiResponse<JsonNode> getDevices() {
        JsonNode data = callApiLive(DEVICES_ENDPOINT, IntegrationClientSupport.DEVICES_FIELD, true);
        return ApiResponse.<JsonNode>builder()
                .success(true)
                .source(SOURCE)
                .message(DEVICES_SUCCESS_MESSAGE)
                .data(data)
                .build();
    }

    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getAlertsFallback")
    public ApiResponse<JsonNode> getAlerts() {
        JsonNode data = callApiLive(ALERTS_ENDPOINT, IntegrationClientSupport.ALERTS_FIELD, false);
        return ApiResponse.<JsonNode>builder()
                .success(true)
                .source(SOURCE)
                .message(ALERTS_SUCCESS_MESSAGE)
                .data(data)
                .build();
    }

    private ApiResponse<JsonNode> getDevicesFallback(Throwable throwable) {
        throw mapCircuitBreakerException("devices API", throwable);
    }

    private ApiResponse<JsonNode> getAlertsFallback(Throwable throwable) {
        throw mapCircuitBreakerException("alerts API", throwable);
    }

    private RuntimeException mapCircuitBreakerException(String apiTarget, Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            log.warn("Observium circuit breaker OPEN on {}: {}", apiTarget, throwable.getMessage());
            return new IntegrationUnavailableException(
                    SOURCE,
                    "Circuit breaker open for Observium " + apiTarget,
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
                "Observium unavailable on " + apiTarget,
                throwable
        );
    }

    private JsonNode callApiLive(String endpoint, String responseField, boolean allowObjectCollection) {
        try {
            String resolvedUrl = buildApiUrl(endpoint);
            String responseBody = webClient.get()
                    .uri(resolvedUrl)
                    .headers(headers -> headers.addAll(createHeaders()))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> Mono.error(new IntegrationUnavailableException(
                                            SOURCE,
                                            "Observium error: " + body
                                    )))
                    )
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .switchIfEmpty(Mono.just(""))
                    .blockOptional()
                    .orElse("");

            if (responseBody == null) {
                throw new IntegrationResponseException(SOURCE, EMPTY_RESPONSE_PREFIX + endpoint);
            }

            if (responseBody.trim().startsWith("<")) {
                throw new IntegrationResponseException(
                        SOURCE,
                        HTML_RESPONSE_TEMPLATE.formatted(endpoint)
                );
            }

            JsonNode root = objectMapper.readTree(responseBody);

            if (root == null || root.isNull()) {
                throw new IntegrationResponseException(SOURCE, NULL_JSON_ROOT_TEMPLATE.formatted(endpoint));
            }

            JsonNode data = root.get(responseField);
            if (data == null || data.isNull()) {
                String status = extractStatus(root);
                log.warn(MISSING_FIELD_LOG_TEMPLATE, endpoint, responseField, status);
                throw new IntegrationResponseException(SOURCE, MISSING_FIELD_TEMPLATE.formatted(responseField, endpoint, status));
            }

            if (data.isArray()) {
                return data;
            }

            if ((allowObjectCollection || IntegrationClientSupport.ALERTS_FIELD.equals(responseField)) && data.isObject()) {
                return normalizeObjectCollection(data);
            }

            log.warn(NOT_ARRAY_LOG_TEMPLATE, responseField, endpoint, data.getNodeType());
            throw new IntegrationResponseException(SOURCE, NOT_ARRAY_TEMPLATE.formatted(responseField, endpoint));

        } catch (IntegrationUnavailableException ex) {
            log.warn(TRANSPORT_ERROR_LOG_TEMPLATE, endpoint, ex.getMessage());
            throw ex;
        } catch (WebClientException ex) {
            log.warn(TIMEOUT_LOG_TEMPLATE, endpoint, ex.getMessage());
            throw new IntegrationTimeoutException(SOURCE, IntegrationClientSupport.timeout(SOURCE_LABEL, endpoint), ex);
        } catch (IntegrationResponseException ex) {
            throw ex;
        } catch (JsonProcessingException ex) {
            log.warn(INVALID_JSON_LOG_TEMPLATE, endpoint, ex.getOriginalMessage());
            throw new IntegrationResponseException(
                    SOURCE,
                    IntegrationClientSupport.invalidJsonResponse(SOURCE_LABEL, endpoint),
                    ex
            );
        }
    }

    private String buildApiUrl(String endpoint) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String normalizedEndpoint = normalizeEndpoint(endpoint);
        String resolvedPath = normalizedBaseUrl.endsWith("/api/v0")
                ? normalizedBaseUrl + normalizedEndpoint.substring("/api/v0".length())
                : normalizedBaseUrl + normalizedEndpoint;

        String trimmedToken = token != null ? token.trim() : "";
        if (trimmedToken.isBlank()) {
            log.warn("Observium token is blank; calling {} without token query parameter", endpoint);
            return resolvedPath;
        }

        String separator = resolvedPath.contains("?") ? "&" : "?";
        return resolvedPath + separator + "token=" + trimmedToken;
    }

    private String normalizeBaseUrl(String rawBaseUrl) {
        String normalized = rawBaseUrl == null ? "" : rawBaseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeEndpoint(String rawEndpoint) {
        String normalized = rawEndpoint == null ? "" : rawEndpoint.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (!normalized.toLowerCase(Locale.ROOT).startsWith("/api/")) {
            normalized = "/api/v0" + normalized;
        }
        return normalized;
    }

    private String extractStatus(JsonNode root) {
        JsonNode statusNode = root.get(IntegrationClientSupport.STATUS_FIELD);
        return statusNode == null || statusNode.isNull()
                ? IntegrationClientSupport.UNKNOWN
                : statusNode.asText();
    }

    private JsonNode normalizeObjectCollection(JsonNode data) {
        ArrayNode normalized = objectMapper.createArrayNode();
        data.elements().forEachRemaining(normalized::add);
        return normalized;
    }
}
