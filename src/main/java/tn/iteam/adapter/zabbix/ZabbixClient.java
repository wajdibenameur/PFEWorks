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

import java.util.*;

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
        Map<String, Object> payload = new HashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("method", method);
        payload.put("id", 1);
        return payload;
    }

    // ------------------- EXECUTION GENERIQUE -------------------
    private JsonNode executeRequest(Map<String, Object> payload, String context) {
        try {
            String prettyPayload = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(payload);

            log.info("Zabbix {} payload:\n{}", context, prettyPayload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(zabbixUrl, request, String.class);

            log.info("HTTP Status: {}", response.getStatusCode());

            if (response.getBody() == null) {
                throw new ZabbixConnectionException(
                        "Empty response from Zabbix (" + context + ")",
                        null
                );
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            if (root.has("result")) {
                return root.get("result");
            }

            if (root.has("error")) {
                throw new ZabbixConnectionException(
                        "Zabbix API error (" + context + "): " + root.get("error"),
                        null
                );
            }

            throw new ZabbixConnectionException(
                    "Unexpected response structure (" + context + ")",
                    null
            );

        } catch (Exception ex) {
            throw new ZabbixConnectionException(
                    "Error while calling Zabbix (" + context + ")",
                    ex
            );
        }
    }

    // ------------------- HOSTS -------------------
    public JsonNode getHosts() {
        Map<String, Object> payload = createBasePayload("host.get");

        Map<String, Object> params = new HashMap<>();
        params.put("output", List.of("hostid", "host", "status"));
        params.put("selectInterfaces", List.of("ip", "main"));

        payload.put("params", params);

        return executeRequest(payload, "hosts");
    }

    // ------------------- PROBLEMS -------------------
    public JsonNode getAllActiveProblems() {
        Map<String, Object> payload = createBasePayload("problem.get");

        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        params.put("selectHosts", "extend");
        params.put("selectTags", "extend");
        params.put("sortfield", "eventid");
        params.put("sortorder", "DESC");
        params.put("limit", 50);
        params.put("recent", true);
        params.put("acknowledged", false);

        params.put("severities", List.of(4, 5));

        payload.put("params", params);

        return executeRequest(payload, "problems");
    }

    // ------------------- VERSION -------------------
    public String getVersion() {
        try {
            Map<String, Object> payload = createBasePayload("apiinfo.version");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(payload, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(zabbixUrl, request, String.class);

            if (response.getBody() == null) {
                throw new ZabbixConnectionException("Empty response", null);
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            if (root.has("result")) {
                return root.get("result").asText();
            }

            if (root.has("error")) {
                throw new ZabbixConnectionException(
                        "Zabbix API error: " + root.get("error"),
                        null
                );
            }

            throw new ZabbixConnectionException("Unexpected response", null);

        } catch (Exception e) {
            throw new ZabbixConnectionException(
                    "Error while fetching Zabbix version",
                    e
            );
        }
    }

    // ------------------- ITEMS -------------------
    public JsonNode getItemsByHost(String hostId) {
        Map<String, Object> payload = createBasePayload("item.get");

        Map<String, Object> params = new HashMap<>();
        params.put("hostids", hostId);
        params.put("output", List.of("itemid", "name", "key_", "value_type"));
        params.put("filter", Map.of("status", 0));

        payload.put("params", params);

        return executeRequest(payload, "items");
    }

    // ------------------- HISTORY -------------------
    public JsonNode getItemHistory(String itemId, int valueType, long from, long to) {
        Map<String, Object> payload = createBasePayload("history.get");

        Map<String, Object> params = new HashMap<>();
        params.put("history", valueType);
        params.put("itemids", itemId);
        params.put("time_from", from);
        params.put("time_till", to);
        params.put("output", "extend");
        params.put("sortfield", "clock");
        params.put("sortorder", "ASC");

        payload.put("params", params);

        return executeRequest(payload, "history");
    }

    public JsonNode getHistoryBatch(List<String> itemIds, int valueType,
                                    long from, long to) {
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

    // ------------------- ITEMS MULTI HOSTS -------------------
    public JsonNode getItemsByHosts(List<String> hostIds) {
        Map<String, Object> payload = createBasePayload("item.get");

        Map<String, Object> params = new HashMap<>();
        params.put("hostids", hostIds);
        params.put("output", List.of("itemid", "name", "key_", "value_type", "hostid"));
        params.put("filter", Map.of("status", 0));

        payload.put("params", params);

        return executeRequest(payload, "items by hosts");
    }

    // ------------------- PROBLEMS BY HOST -------------------
    public JsonNode getActiveProblemsByHost(String hostId) {
        Map<String, Object> payload = createBasePayload("problem.get");

        Map<String, Object> params = new HashMap<>();
        params.put("output", List.of("eventid", "name", "severity", "clock"));
        params.put("hostids", hostId);
        params.put("selectHosts", "extend");
        params.put("sortfield", "clock");
        params.put("sortorder", "DESC");
        params.put("limit", 20);
        params.put("recent", true);
        params.put("acknowledged", false);

        params.put("severities", List.of(4, 5));

        payload.put("params", params);

        return executeRequest(payload, "active problems by host");
    }
}