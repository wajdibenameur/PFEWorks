package tn.iteam.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.zabbix.ZabbixClient;
import tn.iteam.domain.MonitoredHost;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ZabbixSyncService {

    private static final Logger log = LoggerFactory.getLogger(ZabbixSyncService.class);

    private final ZabbixClient client;

    private Map<String, MonitoredHost> cache;
    private long lastLoad = 0;

    public Map<String, MonitoredHost> loadHostMap() {

        if (cache != null && (System.currentTimeMillis() - lastLoad < 30000)) {
            return cache;
        }

        JsonNode hosts = client.getHosts();
        Map<String, MonitoredHost> map = new HashMap<>();

        if (hosts != null && hosts.isArray()) {
            for (JsonNode h : hosts) {
                String hostId = h.path("hostid").asText();
                String name = h.path("host").asText();
                String ip = "IP_UNKNOWN";

                if (h.has("interfaces") && h.get("interfaces").size() > 0) {
                    for (JsonNode iface : h.get("interfaces")) {
                        if (iface.path("main").asInt(0) == 1) {
                            ip = iface.path("ip").asText("IP_UNKNOWN");
                        }
                    }
                }

                MonitoredHost host = MonitoredHost.builder()
                        .hostId(hostId)
                        .name(name)
                        .ip(ip)
                        .source("ZABBIX")
                        .build();

                map.put(hostId, host);
            }
        }

        cache = map;
        lastLoad = System.currentTimeMillis();

        log.info("Loaded {} hosts from Zabbix", map.size());
        return cache;
    }
}