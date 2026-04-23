package tn.iteam.adapter.zabbix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import tn.iteam.domain.MonitoredHost;
import tn.iteam.dto.ZabbixMetricDTO;
import tn.iteam.service.ZabbixSyncService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZabbixAdapterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private ZabbixSyncService syncService;

    @Mock
    private ZabbixClient zabbixClient;

    @Test
    void fetchMetricsAndMapUsesUnitsAndUnsupportedStateFromLiveItems() {
        ZabbixAdapter adapter = new ZabbixAdapter(syncService, zabbixClient);

        JsonNode hosts = OBJECT_MAPPER.createArrayNode()
                .add(OBJECT_MAPPER.createObjectNode()
                        .put("hostid", "101")
                        .put("host", "zbx-host"));

        JsonNode items = OBJECT_MAPPER.createArrayNode()
                .add(OBJECT_MAPPER.createObjectNode()
                        .put("itemid", "2001")
                        .put("name", "CPU utilization")
                        .put("key_", "system.cpu.util[,user]")
                        .put("lastvalue", "42.5")
                        .put("lastclock", "1710000000")
                        .put("value_type", 0)
                        .put("hostid", "101")
                        .put("status", 0)
                        .put("state", 0)
                        .put("units", "%"))
                .add(OBJECT_MAPPER.createObjectNode()
                        .put("itemid", "2002")
                        .put("name", "Ping response")
                        .put("key_", "icmppingsec")
                        .put("lastvalue", "0.123")
                        .put("lastclock", "1710000001")
                        .put("value_type", 0)
                        .put("hostid", "101")
                        .put("status", 0)
                        .put("state", 1)
                        .put("units", "s"));

        when(syncService.loadHostMap(hosts)).thenReturn(Map.of(
                "101",
                MonitoredHost.builder()
                        .hostId("101")
                        .name("zbx-host")
                        .ip("10.0.0.1")
                        .port(10050)
                        .build()
        ));
        when(zabbixClient.getItemsByHosts(List.of("101"))).thenReturn(Mono.just(items));

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
