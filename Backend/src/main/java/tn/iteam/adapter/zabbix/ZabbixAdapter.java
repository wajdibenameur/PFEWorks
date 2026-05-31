package tn.iteam.adapter.zabbix;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZabbixMetricDTO;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.util.MonitoringConstants;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * Zabbix Adapter - Facade compatible for Zabbix integration.
 * Delegates heavy collection logic to specialized collectors:
 * - ZabbixHostCollector: hosts retrieval and mapping
 * - ZabbixProblemCollector: problems retrieval and mapping
 * - ZabbixMetricsCollector: metrics and history retrieval and mapping
 *
 * This class maintains backward compatibility while delegating
 * collection logic to dedicated collector components.
 */
@Component
@RequiredArgsConstructor
public class ZabbixAdapter {

    private static final Logger log = LoggerFactory.getLogger(ZabbixAdapter.class);
    private static final String LOG_PREFIX = "[ZABBIX] ";
    private static final String INTERFACES_FIELD = "interfaces";
    private static final int DEFAULT_MAIN_INTERFACE_FLAG = 1;
    private static final int DEFAULT_ZABBIX_PORT = 10050;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ZabbixHostCollector hostCollector;
    private final ZabbixProblemCollector problemCollector;
    private final ZabbixMetricsCollector metricsCollector;

    // ================== HOSTS ==================

    /**
     * Fetch all hosts from Zabbix and map to DTOs.
     * Delegates to ZabbixHostCollector.
     *
     * @return list of ServiceStatusDTO
     */
    public List<ServiceStatusDTO> fetchAll() {
        return fetchAll(hostCollector.fetchHosts());
    }

    /**
     * Fetch raw hosts payload from Zabbix.
     *
     * @return JsonNode containing hosts array
     */
    public JsonNode fetchHosts() {
        return hostCollector.fetchHosts();
    }

    /**
     * Fetch all hosts from Zabbix using an already-loaded hosts payload and map to DTOs.
     *
     * @param hosts JsonNode containing hosts array
     * @return list of ServiceStatusDTO
     */
    public List<ServiceStatusDTO> fetchAll(JsonNode hosts) {
        List<ServiceStatusDTO> statuses = hostCollector.fetchAll(hosts);
        List<ZabbixProblemDTO> problems = problemCollector.fetchProblems(hosts);
        ZabbixMetricsCollectionResult metricsResult = await(metricsCollector.fetchMetricsCollection(hosts));
        enrichRealStatuses(statuses, problems, metricsResult.metrics(), metricsResult.partial());
        return statuses;
    }

    /**
     * Enrich host statuses with problem and ping context without changing collector responsibilities.
     *
     * Rules:
     * - DOWN if host.status != 0 or icmpping == 0
     * - DEGRADED if host is active and has active problems
     * - UP otherwise
     */
    public List<ServiceStatusDTO> mapHostsToDto(JsonNode hosts) {
        return hostCollector.mapHostsToDto(hosts);
    }

    public ZabbixHostStatusEnrichmentSummary enrichRealStatuses(
            List<ServiceStatusDTO> statuses,
            List<ZabbixProblemDTO> problems,
            List<ZabbixMetricDTO> metrics,
            boolean metricsDegraded
    ) {
        if (statuses == null || statuses.isEmpty()) {
            return new ZabbixHostStatusEnrichmentSummary(0, 0, 0, 0);
        }

        Map<String, Boolean> hasProblemsByHostId = (problems == null ? List.<ZabbixProblemDTO>of() : problems)
                .stream()
                .filter(problem -> problem.getHostId() != null && !problem.getHostId().isBlank())
                .filter(problem -> Boolean.TRUE.equals(problem.getActive())
                        || MonitoringConstants.STATUS_ACTIVE.equalsIgnoreCase(problem.getStatus()))
                .collect(Collectors.toMap(
                        ZabbixProblemDTO::getHostId,
                        problem -> Boolean.TRUE,
                        (left, right) -> left
                ));

        Map<String, ZabbixMetricDTO> pingByHostId = new HashMap<>();
        Map<String, Boolean> hasMetricsByHostId = new HashMap<>();
        if (metrics != null) {
            for (ZabbixMetricDTO metric : metrics) {
                if (metric.getHostId() == null || metric.getHostId().isBlank()) {
                    continue;
                }

                hasMetricsByHostId.put(metric.getHostId(), Boolean.TRUE);

                if (!isPingMetric(metric)) {
                    continue;
                }

                ZabbixMetricDTO existing = pingByHostId.get(metric.getHostId());
                if (existing == null || isMoreRecent(metric, existing)) {
                    pingByHostId.put(metric.getHostId(), metric);
                }
            }
        }

        int downByPing = 0;
        int degradedByProblems = 0;
        int degradedByPartialMetrics = 0;

        for (ServiceStatusDTO status : statuses) {
            String hostId = status.getHostId();
            boolean hostMarkedDown = MonitoringConstants.STATUS_DOWN.equalsIgnoreCase(status.getStatus());
            if (hostMarkedDown) {
                status.setStatus(MonitoringConstants.STATUS_DOWN);
                continue;
            }

            ZabbixMetricDTO pingMetric = hostId == null ? null : pingByHostId.get(hostId);
            if (pingMetric != null && pingMetric.getValue() != null && Double.compare(pingMetric.getValue(), 0.0d) == 0) {
                status.setStatus(MonitoringConstants.STATUS_DOWN);
                downByPing++;
                continue;
            }

            boolean hasActiveProblems = hostId != null && Boolean.TRUE.equals(hasProblemsByHostId.get(hostId));
            if (hasActiveProblems) {
                status.setStatus(MonitoringConstants.STATUS_DEGRADED);
                degradedByProblems++;
                continue;
            }

            boolean degradeFromPartialMetrics = metricsDegraded
                    && hostId != null
                    && Boolean.TRUE.equals(hasMetricsByHostId.get(hostId));

            if (degradeFromPartialMetrics) {
                status.setStatus(MonitoringConstants.STATUS_DEGRADED);
                degradedByPartialMetrics++;
                continue;
            }

            status.setStatus(MonitoringConstants.STATUS_UP);
        }

        ZabbixHostStatusEnrichmentSummary summary = new ZabbixHostStatusEnrichmentSummary(
                statuses.size(),
                downByPing,
                degradedByProblems,
                degradedByPartialMetrics
        );
        log.info(LOG_PREFIX + "Enriched {} hosts, downByPing={}, degradedByProblems={}, degradedByPartialMetrics={}",
                summary.hostsEnriched(),
                summary.downByPing(),
                summary.degradedByProblems(),
                summary.degradedByPartialMetrics());
        return summary;
    }

    // ================== PROBLEMS ==================

    /**
     * Fetch all problems from Zabbix using current hosts.
     * Delegates to ZabbixProblemCollector.
     *
     * @return list of ZabbixProblemDTO
     */
    public List<ZabbixProblemDTO> fetchProblems() {
        return problemCollector.fetchProblems();
    }

    /**
     * Fetch problems with pre-resolved hosts map.
     * Delegates to ZabbixProblemCollector.
     *
     * @param hosts JsonNode containing hosts array
     * @return list of ZabbixProblemDTO
     */
    public List<ZabbixProblemDTO> fetchProblems(JsonNode hosts) {
        return problemCollector.fetchProblems(hosts);
    }

    // ================== METRICS ==================

    /**
     * Fetch all metrics from Zabbix using current hosts.
     * Delegates to ZabbixMetricsCollector.
     *
     * @return Mono of list of ZabbixMetricDTO
     */
    public Mono<List<ZabbixMetricDTO>> fetchMetricsAndMap() {
        return metricsCollector.fetchMetricsAndMap();
    }

    /**
     * Fetch metrics with pre-resolved hosts.
     * Delegates to ZabbixMetricsCollector.
     *
     * @param hosts JsonNode containing hosts array
     * @return Mono of list of ZabbixMetricDTO
     */
    public Mono<List<ZabbixMetricDTO>> fetchMetricsAndMap(JsonNode hosts) {
        return metricsCollector.fetchMetricsAndMap(hosts);
    }

    public Mono<ZabbixMetricsCollectionResult> fetchMetricsCollection() {
        return metricsCollector.fetchMetricsCollection();
    }

    public Mono<ZabbixMetricsCollectionResult> fetchMetricsCollection(JsonNode hosts) {
        return metricsCollector.fetchMetricsCollection(hosts);
    }

    // ================== COMPATIBILITY HELPERS ==================
    // Keep these methods for backward compatibility with any direct usage

    /**
     * Build a map of hostId -> JsonNode for quick lookup.
     * Delegates to ZabbixHostCollector.
     *
     * @param hosts JsonNode containing hosts array
     * @return map of hostId to JsonNode
     */
    public Map<String, JsonNode> buildHostMap(JsonNode hosts) {
        return hostCollector.buildHostMap(hosts);
    }

    /**
     * Extract main IP from host node.
     *
     * @param hostNode JsonNode host
     * @return IP address or unknown
     */
    public String extractMainIp(JsonNode hostNode) {
        for (JsonNode iface : hostNode.path(INTERFACES_FIELD)) {
            if (iface.path(MonitoringConstants.MAIN_FIELD).asInt(0) == DEFAULT_MAIN_INTERFACE_FLAG) {
                return iface.path(MonitoringConstants.IP_FIELD).asText(MonitoringConstants.IP_UNKNOWN);
            }
        }
        return MonitoringConstants.IP_UNKNOWN;
    }

    /**
     * Extract main port from host node.
     *
     * @param hostNode JsonNode host
     * @return port number
     */
    public Integer extractMainPort(JsonNode hostNode) {
        for (JsonNode iface : hostNode.path(INTERFACES_FIELD)) {
            if (iface.path(MonitoringConstants.MAIN_FIELD).asInt(0) == DEFAULT_MAIN_INTERFACE_FLAG) {
                return iface.path(MonitoringConstants.PORT_FIELD).asInt(DEFAULT_ZABBIX_PORT);
            }
        }
        return DEFAULT_ZABBIX_PORT;
    }

    /**
     * Format epoch timestamp to date string.
     *
     * @param epoch epoch seconds
     * @return formatted date string
     */
    public String formatDate(long epoch) {
        return Instant.ofEpochSecond(epoch)
                .atZone(ZoneId.systemDefault())
                .format(DATE_TIME_FORMATTER);
    }

    /**
     * Await a Mono result synchronously.
     *
     * @param mono the Mono to await
     * @return the result
     */
    protected <T> T await(Mono<T> mono) {
        try {
            return mono.toFuture().join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw ex;
        }
    }

    private boolean isPingMetric(ZabbixMetricDTO metric) {
        String metricKey = metric.getMetricKey();
        if (metricKey == null || metricKey.isBlank()) {
            return false;
        }

        String normalized = metricKey.toLowerCase();
        return normalized.startsWith("icmpping")
                && !normalized.startsWith("icmppingloss")
                && !normalized.startsWith("icmppingsec");
    }

    private boolean isMoreRecent(ZabbixMetricDTO left, ZabbixMetricDTO right) {
        long leftTimestamp = left.getTimestamp() != null ? left.getTimestamp() : Long.MIN_VALUE;
        long rightTimestamp = right.getTimestamp() != null ? right.getTimestamp() : Long.MIN_VALUE;
        return leftTimestamp > rightTimestamp;
    }
}
