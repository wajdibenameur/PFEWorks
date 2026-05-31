package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.domain.MonitoredHost;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.repository.MonitoredHostRepository;
import tn.iteam.service.MonitoredHostPersistenceService;
import tn.iteam.util.MonitoringNormalizeUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.db.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MonitoredHostPersistenceServiceImpl implements MonitoredHostPersistenceService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MonitoredHostPersistenceServiceImpl.class);
    private static final int PERSISTENCE_CHUNK_SIZE = 500;

    private final MonitoredHostRepository monitoredHostRepository;

    @Override
    @Transactional
    public int saveAll(String source, List<ServiceStatusDTO> statuses) {
        if (source == null || source.isBlank() || statuses == null || statuses.isEmpty()) {
            return 0;
        }
        long startedAt = System.currentTimeMillis();

        Map<String, ServiceStatusDTO> deduplicated = new LinkedHashMap<>();
        for (ServiceStatusDTO status : statuses) {
            String hostId = resolveHostId(status);
            if (hostId == null) {
                continue;
            }
            deduplicated.put(hostId, status);
        }

        Map<String, MonitoredHost> existingByHostId = monitoredHostRepository.findBySourceAndHostIdIn(
                        source,
                        List.copyOf(deduplicated.keySet())
                ).stream()
                .collect(Collectors.toMap(MonitoredHost::getHostId, Function.identity(), (left, right) -> left));

        List<MonitoredHost> entitiesToSave = new java.util.ArrayList<>(deduplicated.size());
        for (Map.Entry<String, ServiceStatusDTO> entry : deduplicated.entrySet()) {
            String hostId = entry.getKey();
            ServiceStatusDTO dto = entry.getValue();

            String incomingName = MonitoringNormalizeUtils.normalizeText(dto.getName());
            String incomingIp = MonitoringNormalizeUtils.normalizeIp(dto.getIp());
            Integer incomingPort = dto.getPort();

            MonitoredHost existing = existingByHostId.get(hostId);
            MonitoredHost entity = existing != null
                    ? merge(existing, incomingName, incomingIp, incomingPort)
                    : MonitoredHost.builder()
                    .hostId(hostId)
                    .name(incomingName != null ? incomingName : hostId)
                    .ip(incomingIp)
                    .port(incomingPort)
                    .source(source)
                    .build();
            entitiesToSave.add(entity);
        }

        int saved = 0;
        for (int index = 0; index < entitiesToSave.size(); index += PERSISTENCE_CHUNK_SIZE) {
            int end = Math.min(index + PERSISTENCE_CHUNK_SIZE, entitiesToSave.size());
            saved += monitoredHostRepository.saveAll(entitiesToSave.subList(index, end)).size();
        }

        log.info("MonitoredHost batch persistence source={} rows={} durationMs={}",
                source, saved, System.currentTimeMillis() - startedAt);
        return saved;
    }

    private MonitoredHost merge(MonitoredHost existing, String incomingName, String incomingIp, Integer incomingPort) {
        if (incomingName != null) {
            existing.setName(incomingName);
        }
        if (incomingIp != null) {
            existing.setIp(incomingIp);
        } else if (MonitoringNormalizeUtils.normalizeIp(existing.getIp()) == null) {
            existing.setIp(null);
        }
        if (incomingPort != null) {
            existing.setPort(incomingPort);
        }
        return existing;
    }

    private String resolveHostId(ServiceStatusDTO dto) {
        String explicitHostId = MonitoringNormalizeUtils.normalizeText(dto.getHostId());
        if (explicitHostId != null) {
            return explicitHostId;
        }

        String ip = MonitoringNormalizeUtils.normalizeIp(dto.getIp());
        if (ip != null) {
            return ip;
        }

        return MonitoringNormalizeUtils.normalizeText(dto.getName());
    }
}
