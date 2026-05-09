package tn.iteam.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tn.iteam.domain.MonitoredHost;
import tn.iteam.service.ZabbixHostSyncService;
import tn.iteam.util.MonitoringConstants;
import tn.iteam.util.MonitoringNormalizeUtils;

import java.util.HashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(
        name = "app.db.enabled",
        havingValue = "false"
)
public class InMemoryZabbixHostSyncService implements ZabbixHostSyncService {

    private volatile Map<String, MonitoredHost> cache = new HashMap<>();

    @Override
    public Map<String, MonitoredHost> loadHostMap() {
        return cache;
    }

    @Override
    public Map<String, MonitoredHost> loadHostMap(JsonNode hosts) {
        if (hosts == null || !hosts.isArray()) {
            return cache;
        }

        Map<String, MonitoredHost> map = new HashMap<>();

        for (JsonNode h : hosts) {
            String hostId = h.path("hostid").asText(null);
            if (hostId == null || hostId.isBlank()) {
                continue;
            }

            String name = MonitoringNormalizeUtils.normalizeText(h.path("host").asText(null));
            String ip = null;
            Integer port = null;

            if (h.has("interfaces") && h.get("interfaces").size() > 0) {
                for (JsonNode iface : h.get("interfaces")) {
                    if (iface.path("main").asInt(0) == 1) {
                        ip = MonitoringNormalizeUtils.normalizeIp(iface.path("ip").asText(null));
                        port = iface.path("port").isMissingNode() ? null : iface.path("port").asInt();
                    }
                }
            }

            MonitoredHost host = MonitoredHost.builder()
                    .hostId(hostId)
                    .name(name != null ? name : hostId)
                    .ip(ip)
                    .port(port)
                    .source(MonitoringConstants.SOURCE_ZABBIX)
                    .build();

            map.put(hostId, host);
        }

        cache = map;
        return cache;
    }
}
