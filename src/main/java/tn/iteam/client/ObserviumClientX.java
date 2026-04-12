package tn.iteam.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tn.iteam.domain.ApiResponse;
import tn.iteam.exception.ObserviumConnectionException;

@Slf4j
@Component
public class ObserviumClientX {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private final String baseUrl;
    private final String token;

    private static final String DEVICES_ENDPOINT = "/api/v0/devices";
    private static final String ALERTS_ENDPOINT = "/api/v0/alerts";

    public ObserviumClientX(RestTemplate restTemplate,
                            ObjectMapper objectMapper,
                            @Value("${observium.url}") String baseUrl,
                            @Value("${observium.token}") String token) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.token = token;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Auth-Token", token);
        return headers;
    }

    // ================= DEVICES =================
    public ApiResponse<JsonNode> getDevices() {
        try {
            JsonNode data = callApi(DEVICES_ENDPOINT);

            return ApiResponse.<JsonNode>builder()
                    .success(true)
                    .source("OBSERVIUM")
                    .message("Devices fetched successfully")
                    .data(data)
                    .build();

        } catch (Exception e) {
            throw new ObserviumConnectionException("Observium Devices unreachable", e);
        }
    }

    // ================= ALERTS =================
    public ApiResponse<JsonNode> getAlerts() {
        try {
            JsonNode data = callApi(ALERTS_ENDPOINT);

            return ApiResponse.<JsonNode>builder()
                    .success(true)
                    .source("OBSERVIUM")
                    .message("Alerts fetched successfully")
                    .data(data)
                    .build();

        } catch (Exception e) {
            throw new ObserviumConnectionException("Observium Alerts unreachable", e);
        }
    }

    // ================= CORE CALL =================
    private JsonNode callApi(String endpoint) {

        String url = baseUrl + endpoint;

        try {
            HttpEntity<String> entity = new HttpEntity<>(createHeaders());

            log.info("➡ Observium request: {}", url);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (response.getBody() == null) {
                throw new ObserviumConnectionException("Empty response from Observium: " + endpoint);
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            // CASE 1: direct array
            if (root.isArray()) {
                return root;
            }

            // CASE 2: generic safe extraction (robust)
            JsonNode firstArray = root.elements().hasNext() ? root.elements().next() : null;

            if (firstArray != null && firstArray.isArray()) {
                return firstArray;
            }

            return objectMapper.createArrayNode();

        } catch (Exception e) {

            log.error("Observium API failure on {}: {}", endpoint, e.getMessage(), e);

            throw new ObserviumConnectionException(
                    "Observium API unreachable: " + endpoint,
                    e
            );
        }
    }
}