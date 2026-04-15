package tn.iteam.adapter.zabbix;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ZabbixClient {

    private static final Logger log = LoggerFactory.getLogger(ZabbixClient.class);
    private static final String SOURCE = "ZABBIX";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${zabbix.url}")
    private String zabbixUrl;

    @Value("${zabbix.usertoken}")
    private String apiToken;

    public ZabbixClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private Map<String, Object> createBasePayload(String method) {
        log.info("[ZABBIX] Creating base payload for method={}", method);

        Map<String, Object> payload = new HashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("method", method);
        payload.put("id", 1);
        return payload;
    }

    private void logResultSummary(String context, JsonNode result) {
        if (result == null) {
            log.warn("[ZABBIX] {} -> result is null", context);
            return;
        }

        if (result.isArray()) {
            log.info("[ZABBIX] {} -> result array size={}", context, result.size());
        } else if (result.isObject()) {
            log.info("[ZABBIX] {} -> result is object", context);
        } else {
            log.info("[ZABBIX] {} -> result type={}, value={}", context, result.getNodeType(), result.asText());
        }
    }

    private JsonNode executeRequest(Map<String, Object> payload, String context) {
        try {
            String prettyPayload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            log.debug("[ZABBIX] START context={} url={}", context, zabbixUrl);
            log.debug("[ZABBIX] {} payload:\n{}", context, prettyPayload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(zabbixUrl, request, String.class);

            if (response.getBody() == null) {
                throw new IntegrationResponseException(SOURCE, "Empty response from Zabbix (" + context + ")");
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            if (root.has("result")) {
                JsonNode result = root.get("result");
                logResultSummary(context, result);
                return result;
            }

            if (root.has("error")) {
                throw new IntegrationResponseException(
                        SOURCE,
                        "Zabbix API error (" + context + "): " + root.get("error")
                );
            }

            throw new IntegrationResponseException(SOURCE, "Unexpected response structure (" + context + ")");
        } catch (HttpStatusCodeException ex) {
            log.warn("[ZABBIX] {} HTTP error {}: {}", context, ex.getStatusCode().value(), ex.getStatusText());
            throw new IntegrationUnavailableException(
                    SOURCE,
                    "Zabbix returned HTTP " + ex.getStatusCode().value() + " during " + context,
                    ex
            );
        } catch (ResourceAccessException ex) {
            log.warn("[ZABBIX] {} timeout/unreachable: {}", context, ex.getMessage());
            throw new IntegrationTimeoutException(SOURCE, "Zabbix timeout during " + context, ex);
        } catch (JsonProcessingException ex) {
            log.warn("[ZABBIX] {} invalid JSON response: {}", context, ex.getOriginalMessage());
            throw new IntegrationResponseException(SOURCE, "Invalid JSON response from Zabbix (" + context + ")", ex);
        } catch (RestClientException ex) {
            log.warn("[ZABBIX] {} transport error: {}", context, ex.getMessage());
            throw new IntegrationUnavailableException(SOURCE, "Unable to reach Zabbix during " + context, ex);
        } catch (IntegrationUnavailableException | IntegrationTimeoutException | IntegrationResponseException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[ZABBIX] Unexpected error during {}: {}", context, ex.getMessage(), ex);
            throw new IntegrationUnavailableException(SOURCE, "Unexpected error while calling Zabbix (" + context + ")", ex);
        }
    }

    public JsonNode getHosts() {
        log.info("[ZABBIX] getHosts() called");

        Map<String, Object> payload = createBasePayload("host.get");
        Map<String, Object> params = new HashMap<>();
        params.put("output", List.of("hostid", "host", "status"));
        params.put("selectInterfaces", List.of("ip", "port", "main"));
        payload.put("params", params);

        return executeRequest(payload, "hosts");
    }

    public JsonNode getHostById(String hostId) {
        log.info("[ZABBIX] getHostById() called with hostId={}", hostId);

        Map<String, Object> payload = createBasePayload("host.get");
        Map<String, Object> params = new HashMap<>();
        params.put("hostids", List.of(hostId));
        params.put("output", List.of("hostid", "host", "status"));
        params.put("selectInterfaces", List.of("ip", "port", "main"));
        payload.put("params", params);

        return executeRequest(payload, "host by id");
    }

    public JsonNode getAllActiveProblems() {
        log.info("[ZABBIX] getAllActiveProblems() called");

        Map<String, Object> payload = createBasePayload("problem.get");
        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        params.put("selectTags", "extend");
        params.put("sortfield", "eventid");
        params.put("sortorder", "DESC");
        params.put("limit", 50);
        params.put("recent", true);
        params.put("acknowledged", false);
        params.put("severities", List.of(3, 4, 5));
        payload.put("params", params);

        return executeRequest(payload, "problems");
    }

    public JsonNode getAllActiveProblemsByHost(String hostId) {
        log.info("[ZABBIX] getAllActiveProblemsByHost() called with hostId={}", hostId);

        Map<String, Object> payload = createBasePayload("problem.get");
        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        params.put("hostids", List.of(hostId));
        params.put("selectTags", "extend");
        params.put("sortfield", "eventid");
        params.put("sortorder", "DESC");
        params.put("limit", 50);
        params.put("recent", true);
        params.put("acknowledged", false);
        params.put("severities", List.of(3, 4, 5));
        payload.put("params", params);

        return executeRequest(payload, "problems by host");
    }

    public JsonNode getTriggerById(String triggerId) {
        log.info("[ZABBIX] getTriggerById() called with triggerId={}", triggerId);

        Map<String, Object> payload = createBasePayload("trigger.get");
        Map<String, Object> params = new HashMap<>();
        params.put("triggerids", List.of(triggerId));
        params.put("output", List.of("triggerid", "description"));
        params.put("selectHosts", List.of("hostid", "host"));
        payload.put("params", params);

        return executeRequest(payload, "trigger by id");
    }

    public String getVersion() {
        try {
            Map<String, Object> payload = createBasePayload("apiinfo.version");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(zabbixUrl, request, String.class);

            if (response.getBody() == null) {
                throw new IntegrationResponseException(SOURCE, "Empty response from Zabbix version API");
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("result")) {
                return root.get("result").asText();
            }
            if (root.has("error")) {
                throw new IntegrationResponseException(SOURCE, "Zabbix API error: " + root.get("error"));
            }
            throw new IntegrationResponseException(SOURCE, "Unexpected response from Zabbix version API");
        } catch (HttpStatusCodeException ex) {
            log.warn("[ZABBIX] getVersion HTTP error {}: {}", ex.getStatusCode().value(), ex.getStatusText());
            throw new IntegrationUnavailableException(SOURCE, "Zabbix returned HTTP " + ex.getStatusCode().value() + " during getVersion", ex);
        } catch (ResourceAccessException ex) {
            log.warn("[ZABBIX] getVersion timeout/unreachable: {}", ex.getMessage());
            throw new IntegrationTimeoutException(SOURCE, "Zabbix timeout during getVersion", ex);
        } catch (JsonProcessingException ex) {
            log.warn("[ZABBIX] getVersion invalid JSON response: {}", ex.getOriginalMessage());
            throw new IntegrationResponseException(SOURCE, "Invalid JSON response from Zabbix version API", ex);
        } catch (RestClientException ex) {
            log.warn("[ZABBIX] getVersion transport error: {}", ex.getMessage());
            throw new IntegrationUnavailableException(SOURCE, "Unable to reach Zabbix during getVersion", ex);
        } catch (IntegrationUnavailableException | IntegrationTimeoutException | IntegrationResponseException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[ZABBIX] Unexpected getVersion error: {}", ex.getMessage(), ex);
            throw new IntegrationUnavailableException(SOURCE, "Unexpected error while fetching Zabbix version", ex);
        }
    }

    public JsonNode getItemsByHost(String hostId) {
        log.info("[ZABBIX] getItemsByHost() called with hostId={}", hostId);

        Map<String, Object> payload = createBasePayload("item.get");
        Map<String, Object> params = new HashMap<>();
        params.put("hostids", List.of(hostId));
        params.put("output", List.of("itemid", "name", "key_", "value_type", "hostid"));
        params.put("filter", Map.of("status", 0));
        payload.put("params", params);

        return executeRequest(payload, "items by host");
    }

    public JsonNode getItemsByHosts(List<String> hostIds) {
        log.info("[ZABBIX] getItemsByHosts() called with hostIds count={}", hostIds != null ? hostIds.size() : 0);

        Map<String, Object> payload = createBasePayload("item.get");
        Map<String, Object> params = new HashMap<>();
        params.put("hostids", hostIds);
        params.put("output", List.of("itemid", "name", "key_", "value_type", "hostid"));
        params.put("filter", Map.of("status", 0));
        payload.put("params", params);

        return executeRequest(payload, "items by hosts");
    }

    public JsonNode getItemHistory(String itemId, int valueType, long from, long to) {
        log.info("[ZABBIX] getItemHistory() called with itemId={}, valueType={}, from={}, to={}",
                itemId, valueType, from, to);

        Map<String, Object> payload = createBasePayload("history.get");
        Map<String, Object> params = new HashMap<>();
        params.put("history", valueType);
        params.put("itemids", List.of(itemId));
        params.put("time_from", from);
        params.put("time_till", to);
        params.put("output", "extend");
        params.put("sortfield", "clock");
        params.put("sortorder", "ASC");
        payload.put("params", params);

        return executeRequest(payload, "history");
    }

    public JsonNode getHistoryBatch(List<String> itemIds, int valueType, long from, long to) {
        log.info("[ZABBIX] getHistoryBatch() called with itemIds count={}, valueType={}, from={}, to={}",
                itemIds != null ? itemIds.size() : 0, valueType, from, to);

        Map<String, Object> payload = createBasePayload("history.get");
        Map<String, Object> params = new HashMap<>();
        params.put("history", valueType);
        params.put("itemids", itemIds);
        params.put("time_from", from);
        params.put("time_till", to);
        params.put("output", "extend");
        payload.put("params", params);

        return executeRequest(payload, "history batch");
    }

    public JsonNode getLastItemValue(String itemId, int valueType) {
        long now = Instant.now().getEpochSecond();
        long oneHourAgo = now - 3600;

        Map<String, Object> payload = createBasePayload("history.get");
        Map<String, Object> params = new HashMap<>();
        params.put("history", valueType);
        params.put("itemids", List.of(itemId));
        params.put("time_from", oneHourAgo);
        params.put("time_till", now);
        params.put("output", "extend");
        params.put("sortfield", "clock");
        params.put("sortorder", "DESC");
        params.put("limit", 1);
        payload.put("params", params);

        return executeRequest(payload, "last item value");
    }
}
