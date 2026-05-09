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

@Service
@ConditionalOnProperty(name = "app.db.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MonitoredHostPersistenceServiceImpl implements MonitoredHostPersistenceService {

    private final MonitoredHostRepository monitoredHostRepository;

    @Override
    @Transactional
    public int saveAll(String source, List<ServiceStatusDTO> statuses) {
        if (source == null || source.isBlank() || statuses == null || statuses.isEmpty()) {
            return 0;
        }

        Map<String, ServiceStatusDTO> deduplicated = new LinkedHashMap<>();
        for (ServiceStatusDTO status : statuses) {
            String hostId = resolveHostId(status);
            if (hostId == null) {
                continue;
            }
            deduplicated.put(hostId, status);
        }

        int saved = 0;
        for (Map.Entry<String, ServiceStatusDTO> entry : deduplicated.entrySet()) {
            String hostId = entry.getKey();
            ServiceStatusDTO dto = entry.getValue();

            String incomingName = MonitoringNormalizeUtils.normalizeText(dto.getName());
            String incomingIp = MonitoringNormalizeUtils.normalizeIp(dto.getIp());
            Integer incomingPort = dto.getPort();

            MonitoredHost entity = monitoredHostRepository.findFirstByHostIdAndSource(hostId, source)
                    .map(existing -> merge(existing, incomingName, incomingIp, incomingPort))
                    .orElseGet(() -> MonitoredHost.builder()
                            .hostId(hostId)
                            .name(incomingName != null ? incomingName : hostId)
                            .ip(incomingIp)
                            .port(incomingPort)
                            .source(source)
                            .build());

            monitoredHostRepository.save(entity);
            saved++;
        }

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
