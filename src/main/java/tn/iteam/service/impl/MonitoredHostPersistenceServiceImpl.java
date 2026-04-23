package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.domain.MonitoredHost;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.repository.MonitoredHostRepository;
import tn.iteam.service.MonitoredHostPersistenceService;
import tn.iteam.util.MonitoringConstants;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
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

            String incomingName = normalizeText(dto.getName());
            String incomingIp = normalizeIp(dto.getIp());
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
        } else if (normalizeIp(existing.getIp()) == null) {
            existing.setIp(null);
        }
        if (incomingPort != null) {
            existing.setPort(incomingPort);
        }
        return existing;
    }

    private String resolveHostId(ServiceStatusDTO dto) {
        String ip = normalizeIp(dto.getIp());
        if (ip != null) {
            return ip;
        }

        return normalizeText(dto.getName());
    }

    private String normalizeIp(String value) {
        String normalized = normalizeText(value);
        if (normalized == null || MonitoringConstants.IP_UNKNOWN.equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
