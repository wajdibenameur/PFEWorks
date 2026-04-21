package tn.iteam.adapter.zabbix;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Mono;
import tn.iteam.exception.IntegrationResponseException;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;
import tn.iteam.util.IntegrationClientSupport;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.time.Duration;
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
    private static final String CONTEXT_HOSTS = "hosts";
    private static final String CONTEXT_HOST_BY_ID = "host by id";
    private static final String CONTEXT_RECENT_PROBLEMS = "recent problems";
    private static final String CONTEXT_RECENT_PROBLEMS_BY_HOST = "recent problems by host";
    private static final String CONTEXT_TRIGGER_BY_ID = "trigger by id";
    private static final String CONTEXT_ITEMS_BY_HOST = "items by host";
    private static final String CONTEXT_ITEMS_BY_HOSTS = "items by hosts";
    private static final String CONTEXT_HISTORY = "history";
    private static final String CONTEXT_HISTORY_BATCH = "history batch";
    private static final String CONTEXT_LAST_ITEM_VALUE = "last item value";
    private static final String CONTEXT_VERSION_API = "version API";
    private static final String METHOD_HISTORY_GET = "history.get";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private static final String RESULT_ARRAY_LOG = "[ZABBIX] {} -> result array size={}";
    private static final String RESULT_OBJECT_LOG = "[ZABBIX] {} -> result is object";
    private static final String RESULT_VALUE_LOG = "[ZABBIX] {} -> result type={}, value={}";

    @Value("${zabbix.url}")
    private String zabbixUrl;

    @Value("${zabbix.usertoken}")
    private String apiToken;

    public ZabbixClient(
            WebClient webClient,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClient;
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
            log.info(RESULT_ARRAY_LOG, context, result.size());

        } else if (result.isObject()) {
            log.info(RESULT_OBJECT_LOG, context);

        } else {
            if (log.isInfoEnabled()) {
                log.info(RESULT_VALUE_LOG, context, result.getNodeType(), result.asText());
            }
        }
    }

    private JsonNode executeRequestLive(Map<String, Object> payload, String context) {
        try {
            String prettyPayload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            log.debug(LOG_PREFIX + "START context={} url={}", context, zabbixUrl);
            log.debug(LOG_PREFIX + "{} payload:\n{}", context, prettyPayload);

            String responseBody = callPost(zabbixUrl, payload, createAuthenticatedHeaders());
            if (responseBody == null) {
                throw new IntegrationResponseException(SOURCE, EMPTY_RESPONSE_TEMPLATE.formatted(context));
            }

            JsonNode root = objectMapper.readTree(responseBody);

            if (root.has(IntegrationClientSupport.RESULT_FIELD)) {
                JsonNode result = root.get(IntegrationClientSupport.RESULT_FIELD);
                logResultSummary(context, result);
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
        } catch (JsonProcessingException ex) {

            throw new IntegrationResponseException(
                    SOURCE,
                    INVALID_JSON_RESPONSE_PREFIX + ' ' + IntegrationClientSupport.parenthesized(context),
                    ex
            );
        } catch (WebClientException ex) {

            throw new IntegrationTimeoutException(
                    SOURCE,
                    IntegrationClientSupport.timeoutDuring(SOURCE_LABEL, context),
                    ex
            );
        } catch (IntegrationUnavailableException | IntegrationTimeoutException | IntegrationResponseException ex) {
            throw ex;
        } catch (Exception ex) {

            throw new IntegrationUnavailableException(
                    SOURCE,
                    UNEXPECTED_ERROR_PREFIX + ' ' + IntegrationClientSupport.parenthesized(context),
                    ex
            );
        }
    }

    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getHostsFallback")
    public JsonNode getHosts() {
        log.info(LOG_PREFIX + "getHosts() called");

        Map<String, Object> payload = createBasePayload("host.get");
        Map<String, Object> params = new HashMap<>();
        params.put(OUTPUT, HOST_OUTPUT);
        params.put(SELECT_INTERFACES, INTERFACE_OUTPUT);
        payload.put(PARAMS, params);

        return executeRequestLive(payload,CONTEXT_HOSTS);
    }

    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getHostByIdFallback")
    public JsonNode getHostById(String hostId) {
        log.info(LOG_PREFIX + "getHostById() called with hostId={}", hostId);

        Map<String, Object> payload = createBasePayload("host.get");
        Map<String, Object> params = new HashMap<>();
        params.put(HOSTIDS, List.of(hostId));
        params.put(OUTPUT, HOST_OUTPUT);
        params.put(SELECT_INTERFACES, INTERFACE_OUTPUT);
        payload.put(PARAMS, params);

        return executeRequestLive(payload,CONTEXT_HOST_BY_ID);
    }

    @Retry(name = RESILIENCE_NAME)
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

        return executeRequestLive(payload,CONTEXT_RECENT_PROBLEMS);
    }


    @Retry(name = RESILIENCE_NAME)
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

        return executeRequestLive(payload, CONTEXT_RECENT_PROBLEMS);
    }
    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getTriggerByIdFallback")
    public JsonNode getTriggerById(String triggerId) {
        log.info(LOG_PREFIX + "getTriggerById() called with triggerId={}", triggerId);

        Map<String, Object> payload = createBasePayload("trigger.get");
        Map<String, Object> params = new HashMap<>();
        params.put("triggerids", List.of(triggerId));
        params.put(OUTPUT, List.of(TRIGGER_ID, DESCRIPTION));
        params.put(SELECT_HOSTS, PROBLEM_HOST_OUTPUT);
        payload.put(PARAMS, params);

        return executeRequestLive(payload,CONTEXT_TRIGGER_BY_ID);
    }

    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getVersionFallback")
    public String getVersion() {
        try {
            Map<String, Object> payload = createBasePayload("apiinfo.version");
            String responseBody = callPost(zabbixUrl, payload, createJsonHeaders());
            if (responseBody == null) {
                throw new IntegrationResponseException(SOURCE, EMPTY_RESPONSE_TEMPLATE.formatted(VERSION_API_TARGET));
            }

            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has(IntegrationClientSupport.RESULT_FIELD)) {
                return root.get(IntegrationClientSupport.RESULT_FIELD).asText();
            }
            if (root.has(IntegrationClientSupport.ERROR_FIELD)) {
                throw new IntegrationResponseException(
                        SOURCE,
                        ERROR_DURING_PREFIX + ": " + root.get(IntegrationClientSupport.ERROR_FIELD)
                );
            }
            throw new IntegrationResponseException(SOURCE, "Unexpected response from Zabbix version API");
        } catch (JsonProcessingException ex) {

            throw new IntegrationResponseException(SOURCE, "Invalid JSON response from Zabbix version API", ex);
        } catch (WebClientException ex) {

            throw new IntegrationTimeoutException(
                    SOURCE,
                    IntegrationClientSupport.timeoutDuring(SOURCE_LABEL, GET_VERSION_CONTEXT),
                    ex
            );
        } catch (IntegrationUnavailableException | IntegrationTimeoutException | IntegrationResponseException ex) {
            throw ex;
        } catch (Exception ex) {

            throw new IntegrationUnavailableException(SOURCE, UNEXPECTED_VERSION_ERROR, ex);
        }
    }

    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getItemsByHostFallback")
    public JsonNode getItemsByHost(String hostId) {
        log.info(LOG_PREFIX + "getItemsByHost() called with hostId={}", hostId);

        Map<String, Object> payload = createBasePayload("item.get");
        Map<String, Object> params = new HashMap<>();
        params.put(HOSTIDS, List.of(hostId));
        params.put(OUTPUT, ITEM_OUTPUT);
        params.put(FILTER, ACTIVE_STATUS_FILTER);
        payload.put(PARAMS, params);

        return executeRequestLive(payload,CONTEXT_ITEMS_BY_HOST);
    }
    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getItemsByHostsFallback")
    public JsonNode getItemsByHosts(List<String> hostIds) {
        log.info(LOG_PREFIX + "getItemsByHosts() called with hostIds count={}", hostIds != null ? hostIds.size() : 0);

        Map<String, Object> payload = createBasePayload("item.get");
        Map<String, Object> params = new HashMap<>();
        params.put(HOSTIDS, hostIds);
        params.put(OUTPUT, ITEM_OUTPUT);
        params.put(FILTER, ACTIVE_STATUS_FILTER);
        payload.put(PARAMS, params);

        return executeRequestLive(payload, CONTEXT_ITEMS_BY_HOSTS);
    }

    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getItemHistoryFallback")
    public JsonNode getItemHistory(String itemId, int valueType, long from, long to) {
        log.info(LOG_PREFIX + "getItemHistory() called with itemId={}, valueType={}, from={}, to={}",
                itemId, valueType, from, to);

        Map<String, Object> payload = createBasePayload(METHOD_HISTORY_GET);
        Map<String, Object> params = new HashMap<>();
        params.put(HISTORY, valueType);
        params.put(ITEMIDS, List.of(itemId));
        params.put(TIME_FROM, from);
        params.put(TIME_TILL, to);
        params.put(OUTPUT, EXTEND);
        params.put(SORT_FIELD, CLOCK);
        params.put(SORT_ORDER, ASC);
        payload.put(PARAMS, params);

        return executeRequestLive(payload, CONTEXT_HISTORY);
    }

    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getHistoryBatchFallback")
    public JsonNode getHistoryBatch(List<String> itemIds, int valueType, long from, long to) {
        log.info(LOG_PREFIX + "getHistoryBatch() called with itemIds count={}, valueType={}, from={}, to={}",
                itemIds != null ? itemIds.size() : 0, valueType, from, to);

        Map<String, Object> payload = createBasePayload(METHOD_HISTORY_GET);
        Map<String, Object> params = new HashMap<>();
        params.put(HISTORY, valueType);
        params.put(ITEMIDS, itemIds);
        params.put(TIME_FROM, from);
        params.put(TIME_TILL, to);
        params.put(OUTPUT, EXTEND);
        payload.put(PARAMS, params);

        return executeRequestLive(payload,CONTEXT_HISTORY_BATCH);
    }

    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "getLastItemValueFallback")
    public JsonNode getLastItemValue(String itemId, int valueType) {
        long now = Instant.now().getEpochSecond();
        long oneHourAgo = now - 3600;

        Map<String, Object> payload = createBasePayload(METHOD_HISTORY_GET);
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

        return executeRequestLive(payload, CONTEXT_LAST_ITEM_VALUE);
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

    private String callPost(String url, Map<String, Object> payload, HttpHeaders headers) {
        return webClient.post()
                .uri(url)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new IntegrationUnavailableException(
                                        SOURCE,
                                        resolveHttpErrorMessage(response.statusCode(), body)
                                )))
                )
                .bodyToMono(String.class)
                .timeout(REQUEST_TIMEOUT)
                .switchIfEmpty(Mono.just(""))
                .blockOptional()
                .orElse("");
    }

    private String resolveHttpErrorMessage(HttpStatusCode statusCode, String body) {
        int status = statusCode.value();
        if (body != null && !body.isBlank()) {
            return IntegrationClientSupport.returnedHttp(SOURCE_LABEL, status, body);
        }
        return IntegrationClientSupport.returnedHttp(SOURCE_LABEL, status, "Zabbix API");
    }
    private RuntimeException mapCircuitBreakerException(String apiTarget, Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {

            return new IntegrationUnavailableException(
                    SOURCE,
                    "Circuit breaker open for " + SOURCE_LABEL + " " + apiTarget,
                    throwable
            );
        }

        if (throwable instanceof IntegrationUnavailableException e) {
            return e;
        }

        if (throwable instanceof IntegrationTimeoutException e) {
            return e;
        }

        if (throwable instanceof IntegrationResponseException e) {
            return e;
        }

        return new IntegrationUnavailableException(
                SOURCE,
                "Unexpected error while calling Zabbix (" + apiTarget + ")",
                throwable
        );
    }
    @SuppressWarnings("unused")
    // Parameter required by Resilience4j fallback signature
    private JsonNode getHostsFallback(Throwable throwable) {
        throw mapCircuitBreakerException(CONTEXT_HOSTS, throwable);
    }
    @SuppressWarnings("unused")
    // Parameter required by Resilience4j fallback signature
    private JsonNode getHostByIdFallback(String hostId, Throwable throwable) {
        throw mapCircuitBreakerException(CONTEXT_HOST_BY_ID, throwable);
    }
    @SuppressWarnings("unused")
    // Parameter required by Resilience4j fallback signature
    private JsonNode getRecentProblemsFallback(Throwable throwable) {
        throw mapCircuitBreakerException(CONTEXT_RECENT_PROBLEMS, throwable);
    }
    @SuppressWarnings("unused")
    // Parameter required by Resilience4j fallback signature
    private JsonNode getRecentProblemsByHostFallback(String hostId, Throwable throwable) {
        throw mapCircuitBreakerException(CONTEXT_RECENT_PROBLEMS_BY_HOST, throwable);
    }
    @SuppressWarnings("unused")
    // Parameter required by Resilience4j fallback signature
    private JsonNode getTriggerByIdFallback(String triggerId, Throwable throwable) {
        throw mapCircuitBreakerException(CONTEXT_TRIGGER_BY_ID, throwable);
    }
    @SuppressWarnings("unused")
    // Parameter required by Resilience4j fallback signature
    private String getVersionFallback(Throwable throwable) {
        throw mapCircuitBreakerException(CONTEXT_VERSION_API, throwable);
    }
    @SuppressWarnings("unused")
    // Parameter required by Resilience4j fallback signature
    private JsonNode getItemsByHostFallback(String hostId, Throwable throwable) {
        throw mapCircuitBreakerException(CONTEXT_ITEMS_BY_HOST, throwable);
    }
    @SuppressWarnings("unused")
    // Parameter required by Resilience4j fallback signature
    private JsonNode getItemsByHostsFallback(List<String> hostIds, Throwable throwable) {
        throw mapCircuitBreakerException(CONTEXT_ITEMS_BY_HOSTS, throwable);
    }
    @SuppressWarnings("unused")
    private JsonNode getItemHistoryFallback(String itemId, int valueType, long from, long to, Throwable throwable) {
        throw mapCircuitBreakerException(CONTEXT_HISTORY, throwable);
    }
    @SuppressWarnings("unused")
    // Parameter required by Resilience4j fallback signature
    private JsonNode getHistoryBatchFallback(List<String> itemIds, int valueType, long from, long to, Throwable throwable) {
        throw mapCircuitBreakerException(CONTEXT_HISTORY_BATCH, throwable);
    }
    @SuppressWarnings("unused")
    // Parameter required by Resilience4j fallback signature
    private JsonNode getLastItemValueFallback(String itemId, int valueType, Throwable throwable) {
        throw mapCircuitBreakerException(CONTEXT_LAST_ITEM_VALUE, throwable);
    }
}
