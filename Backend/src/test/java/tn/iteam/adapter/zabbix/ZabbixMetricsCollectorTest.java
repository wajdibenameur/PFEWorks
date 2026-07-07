package tn.iteam.adapter.zabbix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.iteam.domain.MonitoredHost;
import tn.iteam.dto.ZabbixMetricDTO;
import tn.iteam.util.MonitoringConstants;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ZabbixMetricsCollectorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ZabbixMetricsCollector collector;
    private Map<String, String> hostNames;
    private Map<String, MonitoredHost> hostMap;

    @BeforeEach
    void setUp() {
        collector = new ZabbixMetricsCollector(
                mock(ZabbixClient.class),
                mock(tn.iteam.service.ZabbixHostSyncService.class),
                mock(ZabbixSyncStateService.class)
        );
        hostNames = Map.of("101", "host-101");
        hostMap = Map.of("101", MonitoredHost.builder()
                .hostId("101")
                .name("host-101")
                .ip("10.0.0.1")
                .port(10050)
                .source(MonitoringConstants.SOURCE_ZABBIX)
                .build());
    }

    @Test
    void itemWithNumericValueAndInvalidLastClock_usesFallbackTimestamp() {
        JsonNode item = item("101", "2001", "agent.ping", "Agent ping", 3, "1", "0");

        var attempt = collector.buildMetricFromItem(item, hostNames, hostMap, "Agent ping", "agent.ping", 3, "UP", null);

        assertThat(attempt.accepted()).isTrue();
        assertThat(attempt.usedFallbackClock()).isTrue();
        assertThat(attempt.dto().getTimestamp()).isPositive();
    }

    @Test
    void systemCpuLoadWithParams_isAccepted() {
        assertThat(collector.isUsefulMetric("system.cpu.load[all,avg1]")).isTrue();
    }

    @Test
    void netIfInWithParams_isAccepted() {
        assertThat(collector.isUsefulMetric("net.if.in[\"eth0\"]")).isTrue();
    }

    @Test
    void agentPing_isAccepted() {
        assertThat(collector.isUsefulMetric("agent.ping")).isTrue();
    }

    @Test
    void agentHostname_remainsRejected() {
        assertThat(collector.isUsefulMetric("agent.hostname")).isFalse();
    }

    @Test
    void nonNumericItem_remainsRejected() {
        JsonNode item = item("101", "2002", "agent.ping", "Agent ping", 3, "not-a-number", "1710000000");

        var attempt = collector.buildMetricFromItem(item, hostNames, hostMap, "Agent ping", "agent.ping", 3, "UP", null);

        assertThat(attempt.accepted()).isFalse();
        assertThat(attempt.rejectReason()).isEqualTo("lastvalue is not numeric");
    }

    @Test
    void netIfOutWithParams_isMapped() {
        JsonNode item = item("101", "2003", "net.if.out[\"eth0\"]", "Interface out", 3, "123.5", "1710000000");

        var attempt = collector.buildMetricFromItem(item, hostNames, hostMap, "Interface out", "net.if.out[\"eth0\"]", 3, "UP", "bps");

        assertThat(attempt.accepted()).isTrue();
        ZabbixMetricDTO dto = attempt.dto();
        assertThat(dto.getMetricKey()).isEqualTo("net.if.out[\"eth0\"]");
        assertThat(dto.getValue()).isEqualTo(123.5d);
        assertThat(dto.getTimestamp()).isEqualTo(1710000000L);
    }

    private JsonNode item(String hostId, String itemId, String key, String name, int valueType, String lastValue, String lastClock) {
        return OBJECT_MAPPER.createObjectNode()
                .put("hostid", hostId)
                .put("itemid", itemId)
                .put("key_", key)
                .put("name", name)
                .put("value_type", valueType)
                .put("lastvalue", lastValue)
                .put("lastclock", lastClock);
    }
}
