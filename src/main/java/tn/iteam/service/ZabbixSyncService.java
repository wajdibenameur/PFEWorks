package tn.iteam.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.zabbix.ZabbixClient;
import tn.iteam.domain.MonitoredHost;
import tn.iteam.repository.MonitoredHostRepository;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ZabbixSyncService {

    private static final Logger log = LoggerFactory.getLogger(ZabbixSyncService.class);

    private final ZabbixClient client;
    private final MonitoredHostRepository monitoredHostRepository;

    private Map<String, MonitoredHost> cache;
    private long lastLoad = 0;

    public Map<String, MonitoredHost> loadHostMap() {
        if (cache != null && (System.currentTimeMillis() - lastLoad < 30000)) {
            return cache;
        }

        JsonNode hosts = client.getHosts();
        return loadHostMap(hosts);
    }

    public Map<String, MonitoredHost> loadHostMap(JsonNode hosts) {
        if (hosts == null || !hosts.isArray()) {
            return cache != null ? cache : new HashMap<>();
        }

        Map<String, MonitoredHost> map = new HashMap<>();

        for (JsonNode h : hosts) {
            String hostId = h.path("hostid").asText();
            String name = h.path("host").asText();
            String ip = "IP_UNKNOWN";
            Integer port = null;

            if (h.has("interfaces") && h.get("interfaces").size() > 0) {
                for (JsonNode iface : h.get("interfaces")) {
                    if (iface.path("main").asInt(0) == 1) {
                        ip = iface.path("ip").asText("IP_UNKNOWN");
                        port = iface.path("port").isMissingNode() ? null : iface.path("port").asInt();
                    }
                }
            }

            MonitoredHost host = MonitoredHost.builder()
                    .hostId(hostId)
                    .name(name)
                    .ip(ip)
                    .port(port)
                    .source("ZABBIX")
                    .build();

            final String finalName = name;
            final String finalIp = ip;
            final Integer finalPort = port;

            monitoredHostRepository.findFirstByHostIdAndSourceOrderByIdDesc(hostId, "ZABBIX")
                    .map(existing -> {
                        existing.setName(finalName);
                        existing.setIp(finalIp);
                        existing.setPort(finalPort);
                        return monitoredHostRepository.save(existing);
                    })
                    .orElseGet(() -> monitoredHostRepository.save(host));

            map.put(hostId, host);
        }

        cache = map;
        lastLoad = System.currentTimeMillis();

        log.info("Loaded {} hosts from Zabbix", map.size());
        return cache;
    }
}
