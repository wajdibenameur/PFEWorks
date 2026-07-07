package tn.iteam.adapter.zabbix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.iteam.domain.MonitoredHost;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.repository.MonitoredHostRepository;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ZabbixCriticalEventHistoryCollectorTest {

    @Mock
    private ZabbixClient zabbixClient;

    @Mock
    private MonitoredHostRepository monitoredHostRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ZabbixCriticalEventHistoryCollector collector;

    @BeforeEach
    void setUp() {
        collector = new ZabbixCriticalEventHistoryCollector(zabbixClient, monitoredHostRepository);
    }

    @Test
    void mapEventMapsResolvedCriticalEventToProblemDto() throws Exception {
        JsonNode eventNode = objectMapper.readTree("""
                {
                  "eventid": "9001",
                  "clock": "1710000000",
                  "r_clock": "1710000300",
                  "severity": "5",
                  "name": "Firewall unreachable",
                  "r_eventid": "9002",
                  "hosts": [
                    {
                      "hostid": "10767",
                      "host": "ACCESS-CONTROL"
                    }
                  ]
                }
                """);
        Map<String, MonitoredHost> monitoredHostsById = new HashMap<>();
        monitoredHostsById.put("10767", MonitoredHost.builder()
                .hostId("10767")
                .name("ACCESS-CONTROL")
                .ip("192.168.11.8")
                .port(10050)
                .source("ZABBIX")
                .build());

        ZabbixProblemDTO dto = collector.mapEvent(eventNode, monitoredHostsById);

        assertThat(dto).isNotNull();
        assertThat(dto.getProblemId()).isEqualTo("9001");
        assertThat(dto.getEventId()).isEqualTo(9001L);
        assertThat(dto.getHostId()).isEqualTo("10767");
        assertThat(dto.getHost()).isEqualTo("ACCESS-CONTROL");
        assertThat(dto.getDescription()).isEqualTo("Firewall unreachable");
        assertThat(dto.getSeverity()).isEqualTo("5");
        assertThat(dto.getActive()).isFalse();
        assertThat(dto.getStartedAt()).isEqualTo(1710000000L);
        assertThat(dto.getResolvedAt()).isEqualTo(1710000300L);
        assertThat(dto.getStatus()).isEqualTo("RESOLVED");
        assertThat(dto.getIp()).isEqualTo("192.168.11.8");
        assertThat(dto.getPort()).isEqualTo(10050);
    }

    @Test
    void mapEventIgnoresNonCriticalOrInvalidEvents() throws Exception {
        JsonNode nonCritical = objectMapper.readTree("""
                {
                  "eventid": "1",
                  "clock": "1710000000",
                  "severity": "3",
                  "hosts": [{"hostid": "10767", "host": "ACCESS-CONTROL"}],
                  "name": "Average only"
                }
                """);
        JsonNode missingHost = objectMapper.readTree("""
                {
                  "eventid": "2",
                  "clock": "1710000000",
                  "severity": "4",
                  "name": "No host"
                }
                """);

        assertThat(collector.mapEvent(nonCritical, new HashMap<>())).isNull();
        assertThat(collector.mapEvent(missingHost, new HashMap<>())).isNull();
    }
}
