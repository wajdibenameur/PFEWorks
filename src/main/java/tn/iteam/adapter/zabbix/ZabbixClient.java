package tn.iteam.adapter.zabbix;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
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
import tn.iteam.cache.IntegrationCacheService;
import tn.iteam.exception.IntegrationResponseException;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.util.IntegrationClientSupport;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ZabbixClient {

    private static final Logger log = LoggerFactory.getLogger(ZabbixClient.class);
    private static final String SOURCE = "ZABBIX";
    private static final String SOURCE_LABEL = "Zabbix";
    private static final String RESILIENCE_NAME = "zabbixApi";
    private static final String LOG_PREFIX = "[ZABBIX] ";
    private static final String JSON_RPC = "jsonrpc";
    private static final String JSON_RPC_VERSION = "2.0";
    private static final String METHOD = "method";
    private static final String ID = "id";
    private static final int REQUEST_ID = 1;
    private static final String PARAMS = "params";
    private static final String OUTPUT = "output";
    private static final String SELECT_INTERFACES = "selectInterfaces";
    private static final String HOSTIDS = "hostids";
    private static final String SELECT_HOSTS = "selectHosts";
    private static final String SELECT_TAGS = "selectTags";
    private static final String SORT_FIELD = "sortfield";
    private static final String SORT_ORDER = "sortorder";
    private static final String LIMIT = "limit";
    private static final String RECENT = "recent";
    private static final String SEVERITIES = "severities";
    private static final String FILTER = "filter";
    private static final String ITEMIDS = "itemids";
    private static final String TIME_FROM = "time_from";
    private static final String TIME_TILL = "time_till";
    private static final String HISTORY = "history";
    private static final String HOST_ID = "hostid";
    private static final String HOST = "host";
    private static final String STATUS = "status";
    private static final String IP = "ip";
    private static final String PORT = "port";
    private static final String MAIN = "main";
    private static final String EXTEND = "extend";
    private static final String DESC = "DESC";
    private static final String ASC = "ASC";
    private static final String EVENT_ID = "eventid";
    private static final String CLOCK = "clock";
    private static final String ITEM_ID = "itemid";
    private static final String NAME = "name";
    private static final String KEY = "key_";
    private static final String VALUE_TYPE = "value_type";
    private static final String DESCRIPTION = "description";
    private static final String TRIGGER_ID = "triggerid";
    private static final String ERROR_DURING_PREFIX = "Zabbix API error";
    private static final String UNEXPECTED_RESPONSE_STRUCTURE_PREFIX = "Unexpected response structure";
    private static final String EMPTY_RESPONSE_TEMPLATE = "Empty response from Zabbix (%s)";
    private static final String INVALID_JSON_RESPONSE_PREFIX = "Invalid JSON response from Zabbix";
    private static final String UNABLE_TO_REACH_PREFIX = "Unable to reach Zabbix";
    private static final String UNEXPECTED_ERROR_PREFIX = "Unexpected error while calling Zabbix";
    private static final String GET_VERSION_CONTEXT = "getVersion";
    private static final String VERSION_API_TARGET = "version API";
    private static final String UNEXPECTED_VERSION_ERROR = "Unexpected error while fetching Zabbix version";
    private static final List<String> HOST_OUTPUT = List.of(HOST_ID, HOST, STATUS);
    private static final List<String> INTERFACE_OUTPUT = List.of(IP, PORT, MAIN);
    private static final List<String> PROBLEM_HOST_OUTPUT = List.of(HOST_ID, HOST);
    private static final List<Integer> HIGH_SEVERITIES = List.of(3, 4, 5);
    private static final List<String> ITEM_OUTPUT = List.of(ITEM_ID, NAME, KEY, VALUE_TYPE, HOST_ID);
    private static final Map<String, Integer> ACTIVE_STATUS_FILTER = Map.of(STATUS, 0);

    private final RestTemplate restTemplate;
    private final IntegrationCacheService integrationCacheService;
    private final SourceAvailabilityService availabilityService;
    private final ObjectMapper objectMapper;

    @Value("${zabbix.url}")
    private String zabbixUrl;

    @Value("${zabbix.usertoken}")
    private String apiToken;

    public ZabbixClient(
            RestTemplate restTemplate,
            IntegrationCacheService integrationCacheService,
            SourceAvailabilityService availabilityService,
            ObjectMapper objectMapper
    ) {
        this.restTemplate = restTemplate;
        this.integrationCacheService = integrationCacheService;
        this.availabilityService = availabilityService;
        this.objectMapper = objectMapper;
    }

    private Map<String, Object> createBasePayload(String method) {
        log.info(LOG_PREFIX + "Creating base payload for method={}", method);

        Map<String, Object> payload = new HashMap<>();
        payload.put(JSON_RPC, JSON_RPC_VERSION);
        payload.put(METHOD, method);
        payload.put(ID, REQUEST_ID);
        return payload;
    }

    private void logResultSummary(String context, JsonNode result) {
        if (result == null) {
            log.warn(LOG_PREFIX + "{} -> result is null", context);
            return;
        }

        if (result.isArray()) {
            log.info(LOG_PREFIX + "{} -> result array size={}", context, result.size());
        } else if (result.isObject()) {
            log.info(LOG_PREFIX + "{} -> result is object", context);
        } else {
            log.info(LOG_PREFIX + "{} -> result type={}, value={}", context, result.getNodeType(), result.asText());
        }
    }

    private JsonNode executeRequestLive(Map<String, Object> payload, String context, String snapshotKey) {
        try {
            String prettyPayload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            log.debug(LOG_PREFIX + "START context={} url={}", context, zabbixUrl);
            log.debug(LOG_PREFIX + "{} payload:\n{}", context, prettyPayload);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, createAuthenticatedHeaders());
            ResponseEntity<String> response = restTemplate.postForEntity(zabbixUrl, request, String.class);

            if (response.getBody() == null) {
                throw new IntegrationResponseException(SOURCE, EMPTY_RESPONSE_TEMPLATE.formatted(context));
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            if (root.has(IntegrationClientSupport.RESULT_FIELD)) {
                JsonNode result = root.get(IntegrationClientSupport.RESULT_FIELD);
                logResultSummary(context, result);
                integrationCacheService.saveSnapshot(SOURCE, snapshotKey, result);
                markAvailable();
                return result;
            }

            if (root.has(IntegrationClientSupport.ERROR_FIELD)) {
                throw new IntegrationResponseException(
                        SOURCE,
                        ERROR_DURING_PREFIX + ' ' + IntegrationClientSupport.parenthesized(context) + ": "
                                + root.get(IntegrationClientSupport.ERROR_FIELD)
                );
            }

            throw new IntegrationResponseException(
                    SOURCE,
                    UNEXPECTED_RESPONSE_STRUCTURE_PREFIX + ' ' + IntegrationClientSupport.parenthesized(context)
            );
        } catch (HttpStatusCodeException ex) {
            int statusCode = ex.getStatusCode().value();
            log.warn(LOG_PREFIX + "{} HTTP error {}: {}", context, statusCode, ex.getStatusText());
            throw new IntegrationUnavailableException(
                    SOURCE,
                    IntegrationClientSupport.returnedHttpDuring(SOURCE_LABEL, statusCode, context),
                    ex
            );
        } catch (ResourceAccessException ex) {
            log.warn(LOG_PREFIX + "{} timeout/unreachable: {}", context, ex.getMessage());
            throw new IntegrationTimeoutException(
                    SOURCE,
                    IntegrationClientSupport.timeoutDuring(SOURCE_LABEL, context),
                    ex
            );
        } catch (JsonProcessingException ex) {
            log.warn(LOG_PREFIX + "{} invalid JSON response: {}", context, ex.getOriginalMessage());
            throw new IntegrationResponseException(
                    SOURCE,
                    INVALID_JSON_RESPONSE_PREFIX + ' ' + IntegrationClientSupport.parenthesized(context),
                    ex
            );
        } catch (RestClientException ex) {
            log.warn(LOG_PREFIX + "{} transport error: {}", context, ex.getMessage());
            throw new IntegrationUnavailableException(
                    SOURCE,
                    IntegrationClientSupport.duringMessage(UNABLE_TO_REACH_PREFIX, context),
                    ex
            );
        } catch (IntegrationUnavailableException | IntegrationTimeoutException | IntegrationResponseException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error(LOG_PREFIX + "Unexpected error during {}: {}", context, ex.getMessage(), ex);
            throw new IntegrationUnavailableException(
                    SOURCE,
                    UNEXPECTED_ERROR_PREFIX + ' ' + IntegrationClientSupport.parenthesized(context),
                    ex
            );
        }
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "getHostsFallback")
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getHostsFallback")
    public JsonNode getHosts() {
        log.info(LOG_PREFIX + "getHosts() called");

        Map<String, Object> payload = createBasePayload("host.get");
        Map<String, Object> params = new HashMap<>();
        params.put(OUTPUT, HOST_OUTPUT);
        params.put(SELECT_INTERFACES, INTERFACE_OUTPUT);
        payload.put(PARAMS, params);

        return executeRequestLive(payload, "hosts", "hosts");
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "getHostByIdFallback")
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getHostByIdFallback")
    public JsonNode getHostById(String hostId) {
        log.info(LOG_PREFIX + "getHostById() called with hostId={}", hostId);

        Map<String, Object> payload = createBasePayload("host.get");
        Map<String, Object> params = new HashMap<>();
        params.put(HOSTIDS, List.of(hostId));
        params.put(OUTPUT, HOST_OUTPUT);
        params.put(SELECT_INTERFACES, INTERFACE_OUTPUT);
        payload.put(PARAMS, params);

        return executeRequestLive(payload, "host by id", "host-by-id-" + hostId);
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "getRecentProblemsFallback")
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getRecentProblemsFallback")
    public JsonNode getRecentProblems() {
        log.info(LOG_PREFIX + "getRecentProblems() called");

        Map<String, Object> payload = createBasePayload("problem.get");
        Map<String, Object> params = new HashMap<>();
        params.put(OUTPUT, EXTEND);
        params.put(SELECT_HOSTS, PROBLEM_HOST_OUTPUT);
        params.put(SELECT_TAGS, EXTEND);
        params.put(SORT_FIELD, EVENT_ID);
        params.put(SORT_ORDER, DESC);
        params.put(LIMIT, 200);
        params.put(RECENT, true);
        params.put(SEVERITIES, HIGH_SEVERITIES);
        payload.put(PARAMS, params);

        return executeRequestLive(payload, "recent problems", "recent-problems");
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "getRecentProblemsByHostFallback")
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getRecentProblemsByHostFallback")
    public JsonNode getRecentProblemsByHost(String hostId) {
        log.info(LOG_PREFIX + "getRecentProblemsByHost() called with hostId={}", hostId);

        Map<String, Object> payload = createBasePayload("problem.get");
        Map<String, Object> params = new HashMap<>();
        params.put(OUTPUT, EXTEND);
        params.put(HOSTIDS, List.of(hostId));
        params.put(SELECT_HOSTS, PROBLEM_HOST_OUTPUT);
        params.put(SELECT_TAGS, EXTEND);
        params.put(SORT_FIELD, EVENT_ID);
        params.put(SORT_ORDER, DESC);
        params.put(LIMIT, 200);
        params.put(RECENT, true);
        params.put(SEVERITIES, HIGH_SEVERITIES);
        payload.put(PARAMS, params);

        return executeRequestLive(payload, "recent problems by host", "recent-problems-by-host-" + hostId);
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "getTriggerByIdFallback")
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getTriggerByIdFallback")
    public JsonNode getTriggerById(String triggerId) {
        log.info(LOG_PREFIX + "getTriggerById() called with triggerId={}", triggerId);

        Map<String, Object> payload = createBasePayload("trigger.get");
        Map<String, Object> params = new HashMap<>();
        params.put("triggerids", List.of(triggerId));
        params.put(OUTPUT, List.of(TRIGGER_ID, DESCRIPTION));
        params.put(SELECT_HOSTS, PROBLEM_HOST_OUTPUT);
        payload.put(PARAMS, params);

        return executeRequestLive(payload, "trigger by id", "trigger-by-id-" + triggerId);
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "getVersionFallback")
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getVersionFallback")
    public String getVersion() {
        try {
            Map<String, Object> payload = createBasePayload("apiinfo.version");
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, createJsonHeaders());
            ResponseEntity<String> response = restTemplate.postForEntity(zabbixUrl, request, String.class);

            if (response.getBody() == null) {
                throw new IntegrationResponseException(SOURCE, EMPTY_RESPONSE_TEMPLATE.formatted(VERSION_API_TARGET));
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has(IntegrationClientSupport.RESULT_FIELD)) {
                String version = root.get(IntegrationClientSupport.RESULT_FIELD).asText();
                integrationCacheService.saveSnapshot(SOURCE, "version", version);
                markAvailable();
                return version;
            }
            if (root.has(IntegrationClientSupport.ERROR_FIELD)) {
                throw new IntegrationResponseException(
                        SOURCE,
                        ERROR_DURING_PREFIX + ": " + root.get(IntegrationClientSupport.ERROR_FIELD)
                );
            }
            throw new IntegrationResponseException(SOURCE, "Unexpected response from Zabbix version API");
        } catch (HttpStatusCodeException ex) {
            int statusCode = ex.getStatusCode().value();
            log.warn(LOG_PREFIX + "getVersion HTTP error {}: {}", statusCode, ex.getStatusText());
            throw new IntegrationUnavailableException(
                    SOURCE,
                    IntegrationClientSupport.returnedHttpDuring(SOURCE_LABEL, statusCode, GET_VERSION_CONTEXT),
                    ex
            );
        } catch (ResourceAccessException ex) {
            log.warn(LOG_PREFIX + "getVersion timeout/unreachable: {}", ex.getMessage());
            throw new IntegrationTimeoutException(
                    SOURCE,
                    IntegrationClientSupport.timeoutDuring(SOURCE_LABEL, GET_VERSION_CONTEXT),
                    ex
            );
        } catch (JsonProcessingException ex) {
            log.warn(LOG_PREFIX + "getVersion invalid JSON response: {}", ex.getOriginalMessage());
            throw new IntegrationResponseException(SOURCE, "Invalid JSON response from Zabbix version API", ex);
        } catch (RestClientException ex) {
            log.warn(LOG_PREFIX + "getVersion transport error: {}", ex.getMessage());
            throw new IntegrationUnavailableException(
                    SOURCE,
                    IntegrationClientSupport.duringMessage(UNABLE_TO_REACH_PREFIX, GET_VERSION_CONTEXT),
                    ex
            );
        } catch (IntegrationUnavailableException | IntegrationTimeoutException | IntegrationResponseException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error(LOG_PREFIX + "Unexpected getVersion error: {}", ex.getMessage(), ex);
            throw new IntegrationUnavailableException(SOURCE, UNEXPECTED_VERSION_ERROR, ex);
        }
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "getItemsByHostFallback")
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getItemsByHostFallback")
    public JsonNode getItemsByHost(String hostId) {
        log.info(LOG_PREFIX + "getItemsByHost() called with hostId={}", hostId);

        Map<String, Object> payload = createBasePayload("item.get");
        Map<String, Object> params = new HashMap<>();
        params.put(HOSTIDS, List.of(hostId));
        params.put(OUTPUT, ITEM_OUTPUT);
        params.put(FILTER, ACTIVE_STATUS_FILTER);
        payload.put(PARAMS, params);

        return executeRequestLive(payload, "items by host", "items-by-host-" + hostId);
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "getItemsByHostsFallback")
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getItemsByHostsFallback")
    public JsonNode getItemsByHosts(List<String> hostIds) {
        log.info(LOG_PREFIX + "getItemsByHosts() called with hostIds count={}", hostIds != null ? hostIds.size() : 0);

        Map<String, Object> payload = createBasePayload("item.get");
        Map<String, Object> params = new HashMap<>();
        params.put(HOSTIDS, hostIds);
        params.put(OUTPUT, ITEM_OUTPUT);
        params.put(FILTER, ACTIVE_STATUS_FILTER);
        payload.put(PARAMS, params);

        return executeRequestLive(payload, "items by hosts", "items-by-hosts-" + hashItems(hostIds));
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "getItemHistoryFallback")
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getItemHistoryFallback")
    public JsonNode getItemHistory(String itemId, int valueType, long from, long to) {
        log.info(LOG_PREFIX + "getItemHistory() called with itemId={}, valueType={}, from={}, to={}",
                itemId, valueType, from, to);

        Map<String, Object> payload = createBasePayload("history.get");
        Map<String, Object> params = new HashMap<>();
        params.put(HISTORY, valueType);
        params.put(ITEMIDS, List.of(itemId));
        params.put(TIME_FROM, from);
        params.put(TIME_TILL, to);
        params.put(OUTPUT, EXTEND);
        params.put(SORT_FIELD, CLOCK);
        params.put(SORT_ORDER, ASC);
        payload.put(PARAMS, params);

        return executeRequestLive(payload, "history", "history-" + itemId + "-vt-" + valueType + "-from-" + from + "-to-" + to);
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "getHistoryBatchFallback")
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getHistoryBatchFallback")
    public JsonNode getHistoryBatch(List<String> itemIds, int valueType, long from, long to) {
        log.info(LOG_PREFIX + "getHistoryBatch() called with itemIds count={}, valueType={}, from={}, to={}",
                itemIds != null ? itemIds.size() : 0, valueType, from, to);

        Map<String, Object> payload = createBasePayload("history.get");
        Map<String, Object> params = new HashMap<>();
        params.put(HISTORY, valueType);
        params.put(ITEMIDS, itemIds);
        params.put(TIME_FROM, from);
        params.put(TIME_TILL, to);
        params.put(OUTPUT, EXTEND);
        payload.put(PARAMS, params);

        return executeRequestLive(
                payload,
                "history batch",
                "history-batch-vt-" + valueType + "-from-" + from + "-to-" + to + "-items-" + hashItems(itemIds)
        );
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "getLastItemValueFallback")
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getLastItemValueFallback")
    public JsonNode getLastItemValue(String itemId, int valueType) {
        long now = Instant.now().getEpochSecond();
        long oneHourAgo = now - 3600;

        Map<String, Object> payload = createBasePayload("history.get");
        Map<String, Object> params = new HashMap<>();
        params.put(HISTORY, valueType);
        params.put(ITEMIDS, List.of(itemId));
        params.put(TIME_FROM, oneHourAgo);
        params.put(TIME_TILL, now);
        params.put(OUTPUT, EXTEND);
        params.put(SORT_FIELD, CLOCK);
        params.put(SORT_ORDER, DESC);
        params.put(LIMIT, 1);
        payload.put(PARAMS, params);

        return executeRequestLive(payload, "last item value", "last-item-value-" + itemId + "-vt-" + valueType);
    }

    private HttpHeaders createAuthenticatedHeaders() {
        HttpHeaders headers = createJsonHeaders();
        headers.setBearerAuth(apiToken);
        return headers;
    }

    private HttpHeaders createJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void markAvailable() {
        availabilityService.markAvailable(SOURCE);
    }

    private JsonNode getHostsFallback(Throwable throwable) {
        return cachedFallback("hosts", throwable);
    }

    private JsonNode getHostByIdFallback(String hostId, Throwable throwable) {
        return cachedFallback("host-by-id-" + hostId, throwable);
    }

    private JsonNode getRecentProblemsFallback(Throwable throwable) {
        return cachedFallback("recent-problems", throwable);
    }

    private JsonNode getRecentProblemsByHostFallback(String hostId, Throwable throwable) {
        return cachedFallback("recent-problems-by-host-" + hostId, throwable);
    }

    private JsonNode getTriggerByIdFallback(String triggerId, Throwable throwable) {
        return cachedFallback("trigger-by-id-" + triggerId, throwable);
    }

    private String getVersionFallback(Throwable throwable) {
        String reason = fallbackReason(throwable);
        return integrationCacheService.getSnapshot(SOURCE, "version", String.class)
                .map(snapshot -> {
                    availabilityService.markDegraded(SOURCE, reason);
                    log.warn(LOG_PREFIX + "Serving Redis fallback snapshot 'version'");
                    return snapshot;
                })
                .orElseThrow(() -> {
                    markUnavailable(reason);
                    return new IntegrationUnavailableException(
                            SOURCE,
                            reason,
                            throwable instanceof Exception exception ? exception : null
                    );
                });
    }

    private JsonNode getItemsByHostFallback(String hostId, Throwable throwable) {
        return cachedFallback("items-by-host-" + hostId, throwable);
    }

    private JsonNode getItemsByHostsFallback(List<String> hostIds, Throwable throwable) {
        return cachedFallback("items-by-hosts-" + hashItems(hostIds), throwable);
    }

    private JsonNode getItemHistoryFallback(String itemId, int valueType, long from, long to, Throwable throwable) {
        return cachedFallback("history-" + itemId + "-vt-" + valueType + "-from-" + from + "-to-" + to, throwable);
    }

    private JsonNode getHistoryBatchFallback(List<String> itemIds, int valueType, long from, long to, Throwable throwable) {
        return cachedFallback(
                "history-batch-vt-" + valueType + "-from-" + from + "-to-" + to + "-items-" + hashItems(itemIds),
                throwable
        );
    }

    private JsonNode getLastItemValueFallback(String itemId, int valueType, Throwable throwable) {
        return cachedFallback("last-item-value-" + itemId + "-vt-" + valueType, throwable);
    }

    private JsonNode cachedFallback(String snapshotKey, Throwable throwable) {
        String reason = fallbackReason(throwable);
        return integrationCacheService.getSnapshot(SOURCE, snapshotKey, JsonNode.class)
                .map(snapshot -> {
                    availabilityService.markDegraded(SOURCE, reason);
                    log.warn(LOG_PREFIX + "Serving Redis fallback snapshot '{}'", snapshotKey);
                    return snapshot;
                })
                .orElseThrow(() -> {
                    markUnavailable(reason);
                    return new IntegrationUnavailableException(
                            SOURCE,
                            reason,
                            throwable instanceof Exception exception ? exception : null
                    );
                });
    }

    private String fallbackReason(Throwable throwable) {
        return throwable != null && throwable.getMessage() != null && !throwable.getMessage().isBlank()
                ? throwable.getMessage()
                : "Zabbix live API failure";
    }

    private String hashItems(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "none";
        }
        return Integer.toHexString(String.join(",", values).hashCode());
    }

    private void markUnavailable(String message) {
        availabilityService.markUnavailable(SOURCE, message);
    }
}
