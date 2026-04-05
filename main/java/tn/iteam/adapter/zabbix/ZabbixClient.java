package tn.iteam.adapter.zabbix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
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

    // ------------------- Récupération des hosts -------------------
    public JsonNode getHosts() {
        Map<String, Object> payload = createBasePayload("host.get");

        Map<String, Object> params = new HashMap<>();
        params.put("output", Arrays.asList("hostid", "host", "status"));
        params.put("selectInterfaces", Arrays.asList("ip", "main"));
        payload.put("params", params);

        return executeRequest(payload, "hosts");
    }

    // ------------------- Récupération des problèmes actifs -------------------
    public JsonNode getAllActiveProblems() {
        Map<String, Object> payload = createBasePayload("problem.get");

        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        params.put("selectHosts", Arrays.asList("hostid", "host"));
        params.put("selectTags", "extend");
        params.put("sortfield", "eventid");
        params.put("sortorder", "DESC");
        params.put("limit", 50);
        params.put("recent", true);
        params.put("acknowledged", false);

        payload.put("params", params);

        return executeRequest(payload, "problems");
    }

    // ------------------- Méthodes utilitaires -------------------
    private Map<String, Object> createBasePayload(String method) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("method", method);
        payload.put("id", 1);
        return payload;
    }

    private JsonNode executeRequest(Map<String, Object> payload, String context) {
        try {
            String prettyPayload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            log.info(" Zabbix {} payload:\n{}", context, prettyPayload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(zabbixUrl, request, String.class);

            log.info("HTTP Status: {}", response.getStatusCode());
            log.debug("Raw response body: {}", response.getBody());

            if (response.getBody() == null) {
                log.warn("Empty response from Zabbix");
                return objectMapper.createArrayNode();
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            if (root.has("result")) {
                log.info(" Successfully fetched {} {}", root.get("result").size(), context);

                //  AJOUT: afficher chaque problème JSON pour debug
                if ("problems".equals(context) || "active problems by host".equals(context)) {
                    for (JsonNode p : root.get("result")) {
                        log.debug("Problem JSON node: {}", p.toPrettyString());
                    }
                }

                return root.get("result");
            } else if (root.has("error")) {
                log.error(" Zabbix API error: {}", root.get("error").toString());
            }
        } catch (Exception e) {
            log.error(" Exception while fetching {} from Zabbix", context, e);
        }
        return objectMapper.createArrayNode();
    }

    public String getVersion() {
        try {
            Map<String, Object> payload = createBasePayload("apiinfo.version");
            String prettyPayload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            log.info(" Zabbix apiinfo.version request:\n{}", prettyPayload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(zabbixUrl, request, String.class);

            log.info("HTTP Status: {}", response.getStatusCode());
            log.debug("Response body: {}", response.getBody());

            if (response.getBody() == null) {
                log.error(" Empty response from Zabbix version API");
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            if (root.has("result")) {
                String version = root.get("result").asText();
                log.info(" ZABBIX Version: {}", version);
                return version;
            } else if (root.has("error")) {
                log.error(" Zabbix API error on version request: {}", root.get("error").toString());
            } else {
                log.error(" Unexpected response structure: {}", root);
            }
        } catch (Exception e) {
            log.error(" Exception while fetching Zabbix version", e);
        }
        return null;
    }

    // ------------------- Récupération des items d'un host -------------------
    public JsonNode getItemsByHost(String hostId) {
        Map<String, Object> payload = createBasePayload("item.get");

        Map<String, Object> params = new HashMap<>();
        params.put("hostids", hostId);
        params.put("output", Arrays.asList("itemid", "name", "key_", "value_type"));
        params.put("filter", Map.of("status", 0)); // seulement actifs

        payload.put("params", params);

        return executeRequest(payload, "items");
    }

    // ------------------- Récupération de l'historique d'un item -------------------
    public JsonNode getItemHistory(String itemId, int valueType, long timeFrom, long timeTill) {
        Map<String, Object> payload = createBasePayload("history.get");

        Map<String, Object> params = new HashMap<>();
        params.put("history", valueType);
        params.put("itemids", itemId);
        params.put("time_from", timeFrom);
        params.put("time_till", timeTill);
        params.put("output", "extend");
        params.put("sortfield", "clock");
        params.put("sortorder", "ASC");

        payload.put("params", params);

        return executeRequest(payload, "history");
    }

    public JsonNode getHistoryBatch(List<String> itemIds, int valueType,
                                    long timeFrom, long timeTill) {
        Map<String, Object> payload = createBasePayload("history.get");

        Map<String, Object> params = new HashMap<>();
        params.put("history", valueType);
        params.put("itemids", itemIds);
        params.put("time_from", timeFrom);
        params.put("time_till", timeTill);
        params.put("output", "extend");

        payload.put("params", params);

        return executeRequest(payload, "history batch");
    }

    // ------------------- Récupération des items pour plusieurs hosts -------------------
    public JsonNode getItemsByHosts(List<String> hostIds) {
        Map<String, Object> payload = createBasePayload("item.get");

        Map<String, Object> params = new HashMap<>();
        params.put("hostids", hostIds);
        params.put("output", Arrays.asList("itemid", "name", "key_", "value_type", "hostid"));
        params.put("filter", Map.of("status", 0));

        payload.put("params", params);

        return executeRequest(payload, "items by hosts");
    }

    // ------------------- Récupération des problèmes actifs d'un host -------------------
    public JsonNode getActiveProblemsByHost(String hostId) {
        Map<String, Object> payload = createBasePayload("problem.get");

        Map<String, Object> params = new HashMap<>();
        params.put("output", Arrays.asList("eventid","name","severity","clock"));
        params.put("hostids", hostId);
        params.put("selectHosts", Arrays.asList("hostid", "host"));
        params.put("sortfield", "clock");
        params.put("sortorder", "DESC");
        params.put("limit", 20);
        params.put("recent", true);
        params.put("acknowledged", false);

        payload.put("params", params);

        return executeRequest(payload, "active problems by host");
    }
}
