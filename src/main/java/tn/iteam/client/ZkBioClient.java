package tn.iteam.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class ZkBioClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper=new ObjectMapper();

    @Value("${zkbio.url}")
    private String baseUrl;

    @Value("${zkbio.token:}")
    private String apiToken;

    private static final String DEVICES_ENDPOINT = "/api/devices"; // à adapter selon API réelle
    private static final String ALERTS_ENDPOINT = "/api/alerts";   // si ZKBio fournit des alertes

    public JsonNode getDevices() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (!apiToken.isEmpty()) {
                headers.setBearerAuth(apiToken);
            }
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + DEVICES_ENDPOINT,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            return objectMapper.readTree(response.getBody()).get("devices"); // si le JSON contient devices
        } catch (Exception e) {
            return null;
        }
    }

    public JsonNode getAlerts() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (!apiToken.isEmpty()) {
                headers.setBearerAuth(apiToken);
            }
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + ALERTS_ENDPOINT,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            return objectMapper.readTree(response.getBody()).get("alerts"); // si le JSON contient alerts
        } catch (Exception e) {
            return null;
        }
    }
}