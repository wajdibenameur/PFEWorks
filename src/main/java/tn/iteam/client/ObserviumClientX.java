package tn.iteam.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tn.iteam.cache.IntegrationCacheService;
import tn.iteam.domain.ApiResponse;
import tn.iteam.exception.IntegrationResponseException;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.util.IntegrationClientSupport;

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
    private static final String DEVICES_SNAPSHOT_KEY = "devices";
    private static final String ALERTS_SNAPSHOT_KEY = "alerts";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final IntegrationCacheService integrationCacheService;
    private final SourceAvailabilityService availabilityService;
    private final String baseUrl;
    private final String token;

    public ObserviumClientX(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            IntegrationCacheService integrationCacheService,
            SourceAvailabilityService availabilityService,
            @Value("${observium.url}") String baseUrl,
            @Value("${observium.token}") String token
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.integrationCacheService = integrationCacheService;
        this.availabilityService = availabilityService;
        this.baseUrl = baseUrl;
        this.token = token;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(X_AUTH_TOKEN, token);
        return headers;
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "getDevicesFallback")
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getDevicesFallback")
    public ApiResponse<JsonNode> getDevices() {
        JsonNode data = callApiLive(DEVICES_ENDPOINT, IntegrationClientSupport.DEVICES_FIELD, true, DEVICES_SNAPSHOT_KEY);
        return ApiResponse.<JsonNode>builder()
                .success(true)
                .source(SOURCE)
                .message(DEVICES_SUCCESS_MESSAGE)
                .data(data)
                .build();
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "getAlertsFallback")
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getAlertsFallback")
    public ApiResponse<JsonNode> getAlerts() {
        JsonNode data = callApiLive(ALERTS_ENDPOINT, IntegrationClientSupport.ALERTS_FIELD, false, ALERTS_SNAPSHOT_KEY);
        return ApiResponse.<JsonNode>builder()
                .success(true)
                .source(SOURCE)
                .message(ALERTS_SUCCESS_MESSAGE)
                .data(data)
                .build();
    }

    private JsonNode callApiLive(String endpoint, String responseField, boolean allowObjectCollection, String snapshotKey) {
        String url = baseUrl + endpoint;

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(createHeaders()),
                    String.class
            );

            if (response.getBody() == null) {
                throw new IntegrationResponseException(SOURCE, EMPTY_RESPONSE_PREFIX + endpoint);
            }

            JsonNode root = objectMapper.readTree(response.getBody());

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
                integrationCacheService.saveSnapshot(SOURCE, snapshotKey, data);
                markAvailable();
                return data;
            }

            if ((allowObjectCollection || IntegrationClientSupport.ALERTS_FIELD.equals(responseField)) && data.isObject()) {
                JsonNode normalized = normalizeObjectCollection(data);
                integrationCacheService.saveSnapshot(SOURCE, snapshotKey, normalized);
                markAvailable();
                return normalized;
            }

            log.warn(NOT_ARRAY_LOG_TEMPLATE, responseField, endpoint, data.getNodeType());
            throw new IntegrationResponseException(SOURCE, NOT_ARRAY_TEMPLATE.formatted(responseField, endpoint));

        } catch (HttpStatusCodeException ex) {
            int statusCode = ex.getStatusCode().value();
            log.warn(HTTP_ERROR_LOG_TEMPLATE, endpoint, statusCode);
            throw new IntegrationUnavailableException(
                    SOURCE,
                    IntegrationClientSupport.returnedHttp(SOURCE_LABEL, statusCode, endpoint),
                    ex
            );
        } catch (ResourceAccessException ex) {
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
        } catch (RestClientException ex) {
            log.warn(TRANSPORT_ERROR_LOG_TEMPLATE, endpoint, ex.getMessage());
            throw new IntegrationUnavailableException(
                    SOURCE,
                    IntegrationClientSupport.unreachable(SOURCE_LABEL, endpoint),
                    ex
            );
        }
    }

    private ApiResponse<JsonNode> getDevicesFallback(Throwable throwable) {
        return cachedFallbackResponse(DEVICES_SNAPSHOT_KEY, DEVICES_SUCCESS_MESSAGE, throwable);
    }

    private ApiResponse<JsonNode> getAlertsFallback(Throwable throwable) {
        return cachedFallbackResponse(ALERTS_SNAPSHOT_KEY, ALERTS_SUCCESS_MESSAGE, throwable);
    }

    private void markAvailable() {
        availabilityService.markAvailable(SOURCE);
    }

    private ApiResponse<JsonNode> cachedFallbackResponse(String snapshotKey, String successMessage, Throwable throwable) {
        String reason = throwable != null && throwable.getMessage() != null && !throwable.getMessage().isBlank()
                ? throwable.getMessage()
                : "Observium live API failure";

        return integrationCacheService.getSnapshot(SOURCE, snapshotKey, JsonNode.class)
                .map(snapshot -> {
                    availabilityService.markDegraded(SOURCE, reason);
                    log.warn("Observium live API failed, serving Redis fallback snapshot '{}'", snapshotKey);
                    return ApiResponse.<JsonNode>builder()
                            .success(true)
                            .source(SOURCE)
                            .message(successMessage)
                            .data(snapshot)
                            .build();
                })
                .orElseThrow(() -> {
                    markUnavailable(reason);
                    return new IntegrationUnavailableException(
                            SOURCE,
                            reason,
                            throwable instanceof Exception exception ? exception : null
                    );
                });
    }

    private void markUnavailable(String reason) {
        availabilityService.markUnavailable(SOURCE, reason);
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
