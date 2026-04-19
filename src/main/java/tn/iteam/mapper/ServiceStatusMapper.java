package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.domain.ServiceStatus;
import tn.iteam.dto.ServiceStatusDTO;

import java.time.LocalDateTime;

@Component
public class ServiceStatusMapper {

    // Exemple de dépendance si tu veux ajouter des règles de mapping
    private final CategoryResolver categoryResolver;

    public ServiceStatusMapper(CategoryResolver categoryResolver) {
        this.categoryResolver = categoryResolver;
    }

    public ServiceStatus toEntity(ServiceStatusDTO dto) {
        ServiceStatus s = new ServiceStatus();
        s.setSource(dto.getSource());
        s.setName(dto.getName());
        s.setIp(dto.getIp());
        s.setPort(dto.getPort());
        s.setProtocol(dto.getProtocol());
        s.setStatus(dto.getStatus());
        // Utilisation de CategoryResolver pour déterminer la catégorie
        s.setCategory(categoryResolver.resolve(dto));
        s.setLastCheck(LocalDateTime.now());
        return s;
    }

    public void updateEntity(ServiceStatus entity, ServiceStatusDTO dto) {
        entity.setSource(dto.getSource());
        entity.setName(dto.getName());
        entity.setIp(dto.getIp());
        entity.setPort(dto.getPort());
        entity.setProtocol(dto.getProtocol());
        entity.setStatus(dto.getStatus());
        entity.setCategory(categoryResolver.resolve(dto));
        entity.setLastCheck(LocalDateTime.now());
    }

    public ServiceStatusDTO toDTO(ServiceStatus entity) {
        ServiceStatusDTO dto = new ServiceStatusDTO();
        dto.setSource(entity.getSource());
        dto.setName(entity.getName());
        dto.setIp(entity.getIp());
        dto.setPort(entity.getPort());
        dto.setProtocol(entity.getProtocol());
        dto.setStatus(entity.getStatus());
        dto.setCategory(entity.getCategory());
        dto.setLastCheck(entity.getLastCheck());
        return dto;
    }
}
