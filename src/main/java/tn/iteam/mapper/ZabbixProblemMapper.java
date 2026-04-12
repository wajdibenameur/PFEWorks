package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.domain.ZabbixProblem;
import tn.iteam.dto.ZabbixProblemDTO;

@Component
public class ZabbixProblemMapper {

    public ZabbixProblem toEntity(ZabbixProblemDTO dto) {
        return ZabbixProblem.builder()
                .problemId(dto.getProblemId())
                .host(dto.getHost())
                .description(dto.getDescription())
                .severity(dto.getSeverity())
                .active(dto.getActive())
                .source(dto.getSource() != null ? dto.getSource() : "Zabbix")
                .eventId(dto.getEventId())
                .ip(dto.getIp())
                .port(dto.getPort())
                .build();
    }

    public ZabbixProblemDTO toDTO(ZabbixProblem entity) {
        return ZabbixProblemDTO.builder()
                .problemId(entity.getProblemId())
                .host(entity.getHost())
                .description(entity.getDescription())
                .severity(entity.getSeverity())
                .active(entity.getActive())
                .source(entity.getSource())
                .eventId(entity.getEventId())
                .ip(entity.getIp())
                .port(entity.getPort()) //  AJOUTÉ
                .build();
    }

}
