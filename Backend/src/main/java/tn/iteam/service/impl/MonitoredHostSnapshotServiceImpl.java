package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.iteam.domain.MonitoredHost;
import tn.iteam.domain.ServiceStatus;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.repository.MonitoredHostRepository;
import tn.iteam.repository.ServiceStatusRepository;
import tn.iteam.service.MonitoredHostSnapshotService;
import tn.iteam.util.MonitoringConstants;
import tn.iteam.util.MonitoringNormalizeUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MonitoredHostSnapshotServiceImpl implements MonitoredHostSnapshotService {

    private final MonitoredHostRepository monitoredHostRepository;
    private final ServiceStatusRepository serviceStatusRepository;

    @Override
    public List<UnifiedMonitoringHostDTO> loadHosts(MonitoringSourceType source) {
        if (source == null) {
            return List.of();
        }

        List<MonitoredHost> hosts = monitoredHostRepository.findBySourceOrderByNameAsc(source.name());
        List<ServiceStatus> statuses = serviceStatusRepository.findBySourceOrderByIpAscPortAsc(source.name());

        Map<String, ServiceStatus> statusesByIp = new LinkedHashMap<>();
        Map<String, ServiceStatus> statusesByName = new LinkedHashMap<>();
        for (ServiceStatus status : statuses) {
            String ip = MonitoringNormalizeUtils.normalizeIp(status.getIp());
            if (ip != null) {
                statusesByIp.put(ip, status);
            }
            String name = MonitoringNormalizeUtils.normalizeText(status.getName());
            if (name != null) {
                statusesByName.put(name, status);
            }
        }

        return hosts.stream()
                .map(host -> toUnifiedHost(source, host, statusesByIp, statusesByName))
                .toList();
    }

    private UnifiedMonitoringHostDTO toUnifiedHost(
            MonitoringSourceType source,
            MonitoredHost host,
            Map<String, ServiceStatus> statusesByIp,
            Map<String, ServiceStatus> statusesByName
    ) {
        String ip = MonitoringNormalizeUtils.normalizeIp(host.getIp());
        String name = MonitoringNormalizeUtils.normalizeText(host.getName());

        ServiceStatus matchedStatus = ip != null
                ? statusesByIp.get(ip)
                : null;
        if (matchedStatus == null && name != null) {
            matchedStatus = statusesByName.get(name);
        }

        String resolvedIp = ip != null
                ? ip
                : matchedStatus != null ? MonitoringNormalizeUtils.normalizeIp(matchedStatus.getIp()) : null;

        return UnifiedMonitoringHostDTO.builder()
                .id(source.name() + ":" + host.getHostId())
                .source(source)
                .hostId(host.getHostId())
                .name(name)
                .ip(resolvedIp)
                .port(host.getPort() != null ? host.getPort() : matchedStatus != null ? matchedStatus.getPort() : null)
                .protocol(matchedStatus != null ? MonitoringNormalizeUtils.normalizeText(matchedStatus.getProtocol()) : null)
                .status(matchedStatus != null ? MonitoringNormalizeUtils.normalizeText(matchedStatus.getStatus()) : null)
                .category(matchedStatus != null ? normalizeCategory(matchedStatus.getCategory(), source) : normalizeCategory(null, source))
                .lastCheck(matchedStatus != null ? matchedStatus.getLastCheck() : null)
                .build();
    }

    private String normalizeCategory(String value, MonitoringSourceType source) {
        String normalized = MonitoringNormalizeUtils.normalizeText(value);
        if (normalized != null) {
            return normalized;
        }
        return switch (source) {
            case CAMERA -> MonitoringConstants.CATEGORY_CAMERA;
            case ZKBIO -> MonitoringConstants.CATEGORY_ACCESS;
            default -> null;
        };
    }

}
