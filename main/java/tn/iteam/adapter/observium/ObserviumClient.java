package tn.iteam.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

@Component
public class ObserviumClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String baseUrl;
    private final String username;
    private final String password;

    public ObserviumClient(RestTemplate restTemplate,
                           @Value("${observium.url}") String baseUrl,
                           @Value("${observium.username}") String username,
                           @Value("${observium.password}") String password) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
    }

    // Méthode pour créer les headers avec Basic Auth
    private HttpHeaders createHeaders() {
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encodedAuth);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static final String DEVICES_ENDPOINT = "/api/v0/devices";
    private static final String ALERTS_ENDPOINT = "/api/v0/alerts";

    public JsonNode getDevices() {
        try {
            HttpEntity<String> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + DEVICES_ENDPOINT,
                    HttpMethod.GET,
                    entity,
                    String.class
            );
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.has("devices") ? root.get("devices") : objectMapper.createArrayNode();
        } catch (Exception e) {
            e.printStackTrace();
            return objectMapper.createArrayNode();
        }
    }

    public JsonNode getAlerts() {
        try {
            HttpEntity<String> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + ALERTS_ENDPOINT,
                    HttpMethod.GET,
                    entity,
                    String.class
            );
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.has("alerts") ? root.get("alerts") : objectMapper.createArrayNode();
        } catch (Exception e) {
            e.printStackTrace();
            return objectMapper.createArrayNode();
        }
    }
}