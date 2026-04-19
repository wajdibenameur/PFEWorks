package tn.iteam.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
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
import tn.iteam.exception.IntegrationResponseException;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.util.IntegrationClientSupport;

@Slf4j
@Component
@RequiredArgsConstructor
public class ZkBioClientX {

    private static final String SOURCE = "ZKBIO";
    private static final String SOURCE_LABEL = "ZKBio";
    private static final String RESILIENCE_NAME = "zkbioApi";
    private static final String DEVICES_ENDPOINT = "/api/devices";
    private static final String ALERTS_ENDPOINT = "/api/alerts";
    private static final String EMPTY_RESPONSE_TEMPLATE = "Empty response from ZKBio %s API";
    private static final String MISSING_FIELD_TEMPLATE = "ZKBio response missing '%s' field for %s";
    private static final String HTTP_ERROR_LOG_TEMPLATE = "ZKBio HTTP error on {}: {}";
    private static final String TIMEOUT_LOG_TEMPLATE = "ZKBio timeout/unreachable on {}: {}";
    private static final String TRANSPORT_ERROR_LOG_TEMPLATE = "ZKBio transport error on {}: {}";
    private static final String UNEXPECTED_ERROR_LOG_TEMPLATE = "Unexpected ZKBio {} error: {}";
    private static final String DEVICES_SNAPSHOT_KEY = "devices";
    private static final String ALERTS_SNAPSHOT_KEY = "alerts";

    private final RestTemplate restTemplate;
    private final IntegrationCacheService integrationCacheService;
    private final SourceAvailabilityService availabilityService;
    private final ObjectMapper objectMapper;

    @Value("${zkbio.url}")
    private String baseUrl;

    @Value("${zkbio.token:}")
    private String apiToken;

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "getDevicesFallback")
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getDevicesFallback")
    public JsonNode getDevices() {
        return callApiLive(DEVICES_ENDPOINT, IntegrationClientSupport.DEVICES_FIELD, DEVICES_SNAPSHOT_KEY);
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "getAlertsFallback")
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getAlertsFallback")
    public JsonNode getAlerts() {
        return callApiLive(ALERTS_ENDPOINT, IntegrationClientSupport.ALERTS_FIELD, ALERTS_SNAPSHOT_KEY);
    }

    private JsonNode callApiLive(String endpoint, String responseField, String snapshotKey) {
        String apiTarget = IntegrationClientSupport.apiTarget(responseField);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + endpoint,
                    HttpMethod.GET,
                    new HttpEntity<>(createHeaders()),
                    String.class
            );

            if (response.getBody() == null) {
                throw new IntegrationResponseException(SOURCE, EMPTY_RESPONSE_TEMPLATE.formatted(responseField));
            }

            JsonNode payload = extractPayload(response.getBody(), responseField, endpoint);
            integrationCacheService.saveSnapshot(SOURCE, snapshotKey, payload);
            markAvailable();
            return payload;
        } catch (HttpStatusCodeException ex) {
            int statusCode = ex.getStatusCode().value();
            log.warn(HTTP_ERROR_LOG_TEMPLATE, apiTarget, statusCode);
            throw new IntegrationUnavailableException(
                    SOURCE,
                    IntegrationClientSupport.returnedHttp(SOURCE_LABEL, statusCode, apiTarget),
                    ex
            );
        } catch (ResourceAccessException ex) {
            log.warn(TIMEOUT_LOG_TEMPLATE, apiTarget, ex.getMessage());
            throw new IntegrationTimeoutException(SOURCE, IntegrationClientSupport.timeout(SOURCE_LABEL, apiTarget), ex);
        } catch (IntegrationResponseException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn(TRANSPORT_ERROR_LOG_TEMPLATE, apiTarget, ex.getMessage());
            throw new IntegrationUnavailableException(
                    SOURCE,
                    IntegrationClientSupport.unreachable(SOURCE_LABEL, apiTarget),
                    ex
            );
        } catch (Exception ex) {
            log.error(UNEXPECTED_ERROR_LOG_TEMPLATE, apiTarget, ex.getMessage(), ex);
            throw new IntegrationUnavailableException(
                    SOURCE,
                    IntegrationClientSupport.unexpectedError(SOURCE_LABEL, apiTarget),
                    ex
            );
        }
    }

    private JsonNode getDevicesFallback(Throwable throwable) {
        return cachedFallback(DEVICES_SNAPSHOT_KEY, throwable);
    }

    private JsonNode getAlertsFallback(Throwable throwable) {
        return cachedFallback(ALERTS_SNAPSHOT_KEY, throwable);
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!apiToken.isEmpty()) {
            headers.setBearerAuth(apiToken);
        }
        return headers;
    }

    private JsonNode extractPayload(String responseBody, String responseField, String endpoint)
            throws JsonProcessingException {
        JsonNode payload = objectMapper.readTree(responseBody).get(responseField);
        if (payload == null || payload.isNull()) {
            throw new IntegrationResponseException(SOURCE, MISSING_FIELD_TEMPLATE.formatted(responseField, endpoint));
        }
        return payload;
    }

    private void markAvailable() {
        availabilityService.markAvailable(SOURCE);
    }

    private JsonNode cachedFallback(String snapshotKey, Throwable throwable) {
        String reason = throwable != null && throwable.getMessage() != null && !throwable.getMessage().isBlank()
                ? throwable.getMessage()
                : "ZKBio live API failure";

        return integrationCacheService.getSnapshot(SOURCE, snapshotKey, JsonNode.class)
                .map(snapshot -> {
                    availabilityService.markDegraded(SOURCE, reason);
                    log.warn("ZKBio live API failed, serving Redis fallback snapshot '{}'", snapshotKey);
                    return snapshot;
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
}
