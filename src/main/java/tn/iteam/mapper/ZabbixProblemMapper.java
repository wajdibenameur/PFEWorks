package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.domain.ZabbixProblem;
import tn.iteam.dto.ZabbixProblemDTO;

@Component
public class ZabbixProblemMapper {

    public ZabbixProblem toEntity(ZabbixProblemDTO dto) {
        return ZabbixProblem.builder()
                .problemId(dto.getProblemId())
                .hostId(parseHostId(dto.getHostId()))
                .host(dto.getHost())
                .description(dto.getDescription())
                .severity(dto.getSeverity())
                .active(dto.getActive())
                .source(dto.getSource() != null ? dto.getSource() : "Zabbix")
                .eventId(dto.getEventId())
                .ip(dto.getIp())
                .port(dto.getPort())
                .startedAt(dto.getStartedAt())
                .resolvedAt(dto.getResolvedAt())
                .status(dto.getStatus())
                .build();
    }

    public ZabbixProblemDTO toDTO(ZabbixProblem entity) {
        return ZabbixProblemDTO.builder()
                .problemId(entity.getProblemId())
                .host(entity.getHost())
                .hostId(entity.getHostId() != null ? entity.getHostId().toString() : null)
                .description(entity.getDescription())
                .severity(entity.getSeverity())
                .active(entity.getActive())
                .source(entity.getSource())
                .eventId(entity.getEventId())
                .ip(entity.getIp())
                .port(entity.getPort())
                .startedAt(entity.getStartedAt())
                .resolvedAt(entity.getResolvedAt())
                .status(entity.getStatus())
                .build();
    }

    private Long parseHostId(String hostId) {
        if (hostId == null || hostId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(hostId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
