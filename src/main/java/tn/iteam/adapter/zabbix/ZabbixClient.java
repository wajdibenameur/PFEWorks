package tn.iteam.adapter.zabbix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tn.iteam.exception.ZabbixConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ZabbixClient {

    private static final Logger log = LoggerFactory.getLogger(ZabbixClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${zabbix.url}")
    private String zabbixUrl;

    @Value("${zabbix.usertoken}")
    private String apiToken;

    public ZabbixClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ------------------- BASE PAYLOAD -------------------
    private Map<String, Object> createBasePayload(String method) {
        log.info("[ZABBIX] Creating base payload for method={}", method);

        Map<String, Object> payload = new HashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("method", method);
        payload.put("id", 1);

        return payload;
    }

    // ------------------- HELPER LOG RESULT -------------------
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

    // ------------------- EXECUTION GENERIQUE -------------------
    private JsonNode executeRequest(Map<String, Object> payload, String context) {
        try {
            log.info("==================================================");
            log.info("[ZABBIX] START context={}", context);
            log.info("[ZABBIX] URL={}", zabbixUrl);

            String prettyPayload = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(payload);

            log.info("[ZABBIX] {} payload:\n{}", context, prettyPayload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiToken);

            log.info("[ZABBIX] {} headers prepared: Content-Type=application/json, Authorization=Bearer <hidden>", context);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            log.info("[ZABBIX] {} sending HTTP POST...", context);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(zabbixUrl, request, String.class);

            log.info("[ZABBIX] {} HTTP Status: {}", context, response.getStatusCode());
            log.info("[ZABBIX] {} raw response:\n{}", context, response.getBody());

            if (response.getBody() == null) {
                log.error("[ZABBIX] {} empty response body", context);
                throw new ZabbixConnectionException(
                        "Empty response from Zabbix (" + context + ")",
                        null
                );
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            if (root.has("result")) {
                JsonNode result = root.get("result");
                logResultSummary(context, result);
                log.info("[ZABBIX] END context={} SUCCESS", context);
                log.info("==================================================");
                return result;
            }

            if (root.has("error")) {
                log.error("[ZABBIX] {} API returned error: {}", context, root.get("error").toPrettyString());
                throw new ZabbixConnectionException(
                        "Zabbix API error (" + context + "): " + root.get("error"),
                        null
                );
            }

            log.error("[ZABBIX] {} unexpected response structure: {}", context, root.toPrettyString());
            throw new ZabbixConnectionException(
                    "Unexpected response structure (" + context + ")",
                    null
            );

        } catch (Exception ex) {
            log.error("[ZABBIX] END context={} ERROR -> {}", context, ex.getMessage(), ex);
            log.info("==================================================");
            throw new ZabbixConnectionException(
                    "Error while calling Zabbix (" + context + ")",
                    ex
            );
        }
    }

    // ------------------- HOSTS -------------------
    public JsonNode getHosts() {
        log.info("[ZABBIX] getHosts() called");

        Map<String, Object> payload = createBasePayload("host.get");

        Map<String, Object> params = new HashMap<>();
        params.put("output", List.of("hostid", "host", "status"));
        params.put("selectInterfaces", List.of("ip", "port", "main"));

        payload.put("params", params);

        log.info("[ZABBIX] getHosts() params={}", params);

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

        log.info("[ZABBIX] getHostById() params={}", params);

        return executeRequest(payload, "host by id");
    }

    // ------------------- PROBLEMS -------------------
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

        log.info("[ZABBIX] getAllActiveProblems() params={}", params);

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

        log.info("[ZABBIX] getAllActiveProblemsByHost() params={}", params);

        return executeRequest(payload, "problems by host");
    }

    // ------------------- TRIGGERS -------------------
    public JsonNode getTriggerById(String triggerId) {
        log.info("[ZABBIX] getTriggerById() called with triggerId={}", triggerId);

        Map<String, Object> payload = createBasePayload("trigger.get");

        Map<String, Object> params = new HashMap<>();
        params.put("triggerids", List.of(triggerId));
        params.put("output", List.of("triggerid", "description"));
        params.put("selectHosts", List.of("hostid", "host"));

        payload.put("params", params);

        log.info("[ZABBIX] getTriggerById() params={}", params);

        return executeRequest(payload, "trigger by id");
    }

    // ------------------- VERSION -------------------
    public String getVersion() {
        try {
            log.info("==================================================");
            log.info("[ZABBIX] START getVersion()");
            log.info("[ZABBIX] URL={}", zabbixUrl);

            Map<String, Object> payload = createBasePayload("apiinfo.version");

            String prettyPayload = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(payload);
            log.info("[ZABBIX] getVersion payload:\n{}", prettyPayload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(payload, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(zabbixUrl, request, String.class);

            log.info("[ZABBIX] getVersion HTTP Status: {}", response.getStatusCode());
            log.info("[ZABBIX] getVersion raw response:\n{}", response.getBody());

            if (response.getBody() == null) {
                log.error("[ZABBIX] getVersion empty response");
                throw new ZabbixConnectionException("Empty response", null);
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            if (root.has("result")) {
                String version = root.get("result").asText();
                log.info("[ZABBIX] getVersion SUCCESS -> {}", version);
                log.info("==================================================");
                return version;
            }

            if (root.has("error")) {
                log.error("[ZABBIX] getVersion API error: {}", root.get("error").toPrettyString());
                throw new ZabbixConnectionException(
                        "Zabbix API error: " + root.get("error"),
                        null
                );
            }

            log.error("[ZABBIX] getVersion unexpected response: {}", root.toPrettyString());
            throw new ZabbixConnectionException("Unexpected response", null);

        } catch (Exception e) {
            log.error("[ZABBIX] END getVersion ERROR -> {}", e.getMessage(), e);
            log.info("==================================================");
            throw new ZabbixConnectionException(
                    "Error while fetching Zabbix version",
                    e
            );
        }
    }

    // ------------------- ITEMS -------------------
    public JsonNode getItemsByHost(String hostId) {
        log.info("[ZABBIX] getItemsByHost() called with hostId={}", hostId);

        Map<String, Object> payload = createBasePayload("item.get");

        Map<String, Object> params = new HashMap<>();
        params.put("hostids", List.of(hostId));
        params.put("output", List.of("itemid", "name", "key_", "value_type", "hostid"));
        params.put("filter", Map.of("status", 0));

        payload.put("params", params);

        log.info("[ZABBIX] getItemsByHost() params={}", params);

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

        log.info("[ZABBIX] getItemsByHosts() params prepared");

        return executeRequest(payload, "items by hosts");
    }

    // ------------------- HISTORY -------------------
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

        log.info("[ZABBIX] getItemHistory() params={}", params);

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

        log.info("[ZABBIX] getHistoryBatch() params prepared");

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
