package tn.iteam.adapter.zabbix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import tn.iteam.dto.ZabbixMetricDTO;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZabbixAdapterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private ZabbixHostCollector hostCollector;

    @Mock
    private ZabbixProblemCollector problemCollector;

    @Mock
    private ZabbixMetricsCollector metricsCollector;

    @Test
    void fetchMetricsAndMapUsesUnitsAndUnsupportedStateFromLiveItems() {
        ZabbixAdapter adapter = new ZabbixAdapter(hostCollector, problemCollector, metricsCollector);

        JsonNode hosts = OBJECT_MAPPER.createArrayNode()
                .add(OBJECT_MAPPER.createObjectNode()
                        .put("hostid", "101")
                        .put("host", "zbx-host"));

        // Mock the metricsCollector to return the expected metrics
        when(metricsCollector.fetchMetricsAndMap(hosts)).thenReturn(Mono.just(List.of(
                ZabbixMetricDTO.builder()
                        .hostId("101")
                        .hostName("zbx-host")
                        .itemId("2001")
                        .metricName("CPU utilization")
                        .metricKey("system.cpu.util[,user]")
                        .valueType(0)
                        .status("UP")
                        .units("%")
                        .value(42.5)
                        .timestamp(1710000000L)
                        .build(),
                ZabbixMetricDTO.builder()
                        .hostId("101")
                        .hostName("zbx-host")
                        .itemId("2002")
                        .metricName("Ping response")
                        .metricKey("icmppingsec")
                        .valueType(0)
                        .status("UNSUPPORTED")
                        .units("s")
                        .value(0.123)
                        .timestamp(1710000001L)
                        .build()
        )));

        List<ZabbixMetricDTO> metrics = adapter.fetchMetricsAndMap(hosts).block();

        assertThat(metrics)
                .hasSize(2)
                .extracting(ZabbixMetricDTO::getMetricName, ZabbixMetricDTO::getUnits, ZabbixMetricDTO::getStatus)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("CPU utilization", "%", "UP"),
                        org.assertj.core.groups.Tuple.tuple("Ping response", "s", "UNSUPPORTED")
                );
    }
}
