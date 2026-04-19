package tn.iteam.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tn.iteam.cache.IntegrationCacheService;
import tn.iteam.service.SourceAvailabilityService;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
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

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final IntegrationCacheService integrationCacheService;
    private final SourceAvailabilityService availabilityService;

    @Value("${zkbio.url}")
    private String baseUrl;

    @Value("${zkbio.token:}")
    private String apiToken;

    public JsonNode getDevices() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("page", 1);
        payload.put("size", 100);
        return postForList(DEVICE_LIST_ENDPOINT, payload, DEVICES_SNAPSHOT_KEY);
    }

    public JsonNode getStatus() {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + STATUS_ENDPOINT,
                    HttpMethod.GET,
                    new HttpEntity<>(createHeaders()),
                    String.class
            );
            JsonNode payload = parseBody(response.getBody());
            integrationCacheService.saveSnapshot(SOURCE, STATUS_SNAPSHOT_KEY, payload);
            availabilityService.markAvailable(SOURCE);
            return payload;
        } catch (Exception ex) {
            log.error("Error fetching ZKBio status: {}", ex.getMessage());
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
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + endpoint,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, createHeaders()),
                    String.class
            );
            JsonNode result = extractListPayload(parseBody(response.getBody()));
            integrationCacheService.saveSnapshot(SOURCE, snapshotKey, result);
            availabilityService.markAvailable(SOURCE);
            return result;
        } catch (Exception ex) {
            log.error("Error calling ZKBio endpoint {}: {}", endpoint, ex.getMessage());
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

    private JsonNode parseBody(String responseBody) throws Exception {
        if (responseBody == null || responseBody.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(responseBody);
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
