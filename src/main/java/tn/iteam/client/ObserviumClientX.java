package tn.iteam.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import tn.iteam.domain.ApiResponse;
import tn.iteam.exception.IntegrationResponseException;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;
import tn.iteam.service.SourceAvailabilityService;

@Slf4j
@Component
public class ObserviumClientX {

    private static final String SOURCE = "OBSERVIUM";
    private static final String DEVICES_ENDPOINT = "/api/v0/devices";
    private static final String ALERTS_ENDPOINT = "/api/v0/alerts";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SourceAvailabilityService availabilityService;
    private final String baseUrl;
    private final String token;

    public ObserviumClientX(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            SourceAvailabilityService availabilityService,
            @Value("${observium.url}") String baseUrl,
            @Value("${observium.token}") String token
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.availabilityService = availabilityService;
        this.baseUrl = baseUrl;
        this.token = token;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Auth-Token", token);
        return headers;
    }

    public ApiResponse<JsonNode> getDevices() {
        JsonNode data = callApi(DEVICES_ENDPOINT);
        return ApiResponse.<JsonNode>builder()
                .success(true)
                .source(SOURCE)
                .message("Devices fetched successfully")
                .data(data)
                .build();
    }

    public ApiResponse<JsonNode> getAlerts() {
        JsonNode data = callApi(ALERTS_ENDPOINT);
        return ApiResponse.<JsonNode>builder()
                .success(true)
                .source(SOURCE)
                .message("Alerts fetched successfully")
                .data(data)
                .build();
    }

    private JsonNode callApi(String endpoint) {
        String url = baseUrl + endpoint;

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(createHeaders()),
                    String.class
            );

            if (response.getBody() == null) {
                throw new IntegrationResponseException(SOURCE, "Empty response from Observium: " + endpoint);
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            availabilityService.markAvailable(SOURCE);

            if (root.isArray()) {
                return root;
            }

            JsonNode firstArray = root.elements().hasNext() ? root.elements().next() : null;
            if (firstArray != null && firstArray.isArray()) {
                return firstArray;
            }

            throw new IntegrationResponseException(SOURCE, "Unexpected Observium response payload: " + endpoint);
        } catch (HttpStatusCodeException ex) {
            availabilityService.markUnavailable(SOURCE, "HTTP " + ex.getStatusCode().value() + " on " + endpoint);
            log.warn("Observium HTTP error on {}: {}", endpoint, ex.getStatusCode().value());
            throw new IntegrationUnavailableException(SOURCE, "Observium returned HTTP " + ex.getStatusCode().value() + " on " + endpoint, ex);
        } catch (ResourceAccessException ex) {
            availabilityService.markUnavailable(SOURCE, "Timeout on " + endpoint);
            log.warn("Observium timeout/unreachable on {}: {}", endpoint, ex.getMessage());
            throw new IntegrationTimeoutException(SOURCE, "Observium timeout on " + endpoint, ex);
        } catch (JsonProcessingException ex) {
            availabilityService.markUnavailable(SOURCE, "Invalid JSON on " + endpoint);
            log.warn("Observium invalid JSON on {}: {}", endpoint, ex.getOriginalMessage());
            throw new IntegrationResponseException(SOURCE, "Invalid JSON response from Observium on " + endpoint, ex);
        } catch (RestClientException ex) {
            availabilityService.markUnavailable(SOURCE, "Transport error on " + endpoint);
            log.warn("Observium transport error on {}: {}", endpoint, ex.getMessage());
            throw new IntegrationUnavailableException(SOURCE, "Observium unreachable on " + endpoint, ex);
        }
    }
}
