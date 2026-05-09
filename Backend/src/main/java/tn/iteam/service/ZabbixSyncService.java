package tn.iteam.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.zabbix.ZabbixClient;
import tn.iteam.domain.MonitoredHost;
import tn.iteam.repository.MonitoredHostRepository;
import tn.iteam.util.MonitoringConstants;
import tn.iteam.util.MonitoringNormalizeUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
@ConditionalOnProperty(
        name = "app.db.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@Service
@RequiredArgsConstructor
public class ZabbixSyncService implements ZabbixHostSyncService{

    private static final Logger log = LoggerFactory.getLogger(ZabbixSyncService.class);
    private static final long CACHE_TTL_MS = 30_000L;

    private final ZabbixClient client;
    private final MonitoredHostRepository monitoredHostRepository;

    private volatile Map<String, MonitoredHost> cache;
    private volatile long lastLoad = 0;

    public Map<String, MonitoredHost> loadHostMap() {
        if (cache != null && (System.currentTimeMillis() - lastLoad < CACHE_TTL_MS)) {
            return cache;
        }

        JsonNode hosts = await(client.getHosts());
        return loadHostMap(hosts);
    }

    public Map<String, MonitoredHost> loadHostMap(JsonNode hosts) {
        if (hosts == null || !hosts.isArray()) {
            return cache != null ? cache : new HashMap<>();
        }

        Map<String, MonitoredHost> map = new HashMap<>();

        for (JsonNode h : hosts) {
            String hostId = h.path("hostid").asText();
            String name = MonitoringNormalizeUtils.normalizeText(h.path("host").asText());
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

            final String resolvedName = name;
            final String resolvedIp = ip;
            final Integer resolvedPort = port;

            MonitoredHost host = monitoredHostRepository.findFirstByHostIdAndSource(hostId, MonitoringConstants.SOURCE_ZABBIX)
                    .map(existing -> {
                        existing.setName(resolvedName != null ? resolvedName : existing.getName());
                        existing.setIp(resolvedIp != null ? resolvedIp : MonitoringNormalizeUtils.normalizeIp(existing.getIp()));
                        existing.setPort(resolvedPort != null ? resolvedPort : existing.getPort());
                        return monitoredHostRepository.save(existing);
                    })
                    .orElseGet(() -> monitoredHostRepository.save(MonitoredHost.builder()
                            .hostId(hostId)
                            .name(resolvedName != null ? resolvedName : hostId)
                            .ip(resolvedIp)
                            .port(resolvedPort)
                            .source(MonitoringConstants.SOURCE_ZABBIX)
                            .build()));

            map.put(hostId, host);
        }

        cache = map;
        lastLoad = System.currentTimeMillis();

        log.info("Loaded {} hosts from Zabbix", map.size());
        return cache;
    }

    private <T> T await(reactor.core.publisher.Mono<T> mono) {
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
}
