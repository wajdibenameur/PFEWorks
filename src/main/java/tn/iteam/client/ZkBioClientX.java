package tn.iteam.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
import tn.iteam.exception.IntegrationResponseException;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;
import tn.iteam.service.SourceAvailabilityService;

@Component
@RequiredArgsConstructor
public class ZkBioClientX {

    private static final String SOURCE = "ZKBIO";
    private static final String DEVICES_ENDPOINT = "/api/devices";
    private static final String ALERTS_ENDPOINT = "/api/alerts";

    private final RestTemplate restTemplate;
    private final SourceAvailabilityService availabilityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${zkbio.url}")
    private String baseUrl;

    @Value("${zkbio.token:}")
    private String apiToken;

    public JsonNode getDevices() {
        return callApi(DEVICES_ENDPOINT, "devices");
    }

    public JsonNode getAlerts() {
        return callApi(ALERTS_ENDPOINT, "alerts");
    }

    private JsonNode callApi(String endpoint, String responseField) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + endpoint,
                    HttpMethod.GET,
                    new HttpEntity<>(createHeaders()),
                    String.class
            );

            if (response.getBody() == null) {
                throw new IntegrationResponseException(SOURCE, "Empty response from ZKBio " + responseField + " API");
            }

            JsonNode payload = objectMapper.readTree(response.getBody()).get(responseField);
            availabilityService.markAvailable(SOURCE);
            return payload;
        } catch (HttpStatusCodeException ex) {
            availabilityService.markUnavailable(SOURCE, "HTTP " + ex.getStatusCode().value() + " on " + responseField + " API");
            throw new IntegrationUnavailableException(SOURCE, "ZKBio returned HTTP " + ex.getStatusCode().value() + " on " + responseField + " API", ex);
        } catch (ResourceAccessException ex) {
            availabilityService.markUnavailable(SOURCE, "Timeout on " + responseField + " API");
            throw new IntegrationTimeoutException(SOURCE, "ZKBio timeout on " + responseField + " API", ex);
        } catch (IntegrationResponseException ex) {
            availabilityService.markUnavailable(SOURCE, ex.getMessage());
            throw ex;
        } catch (RestClientException ex) {
            availabilityService.markUnavailable(SOURCE, "Transport error on " + responseField + " API");
            throw new IntegrationUnavailableException(SOURCE, "ZKBio unreachable on " + responseField + " API", ex);
        } catch (Exception ex) {
            availabilityService.markUnavailable(SOURCE, "Unexpected " + responseField + " API error");
            throw new IntegrationUnavailableException(SOURCE, "Unexpected ZKBio " + responseField + " API error", ex);
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!apiToken.isEmpty()) {
            headers.setBearerAuth(apiToken);
        }
        return headers;
    }
}
