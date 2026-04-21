package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.domain.ServiceStatus;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.mapper.ServiceStatusMapper;
import tn.iteam.repository.ServiceStatusRepository;
import tn.iteam.service.ServiceStatusPersistenceService;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceStatusPersistenceServiceImpl implements ServiceStatusPersistenceService {

    private static final String STATUS_SAVE_ERROR_TEMPLATE = "Error saving status for {}:{} - {}";

    private final ServiceStatusRepository statusRepository;
    private final ServiceStatusMapper statusMapper;

    @Override
    @Transactional
    public int saveAll(List<ServiceStatusDTO> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return 0;
        }

        int saved = 0;
        for (ServiceStatusDTO dto : statuses) {
            try {
                Optional<ServiceStatus> existing = Optional.empty();
                if (dto.getSource() != null && dto.getName() != null && dto.getIp() != null) {
                    existing = statusRepository.findBySourceAndNameAndIp(
                            dto.getSource(), dto.getName(), dto.getIp());
                } else if (dto.getSource() != null && dto.getIp() != null && dto.getPort() != null) {
                    existing = statusRepository.findBySourceAndIpAndPort(
                            dto.getSource(), dto.getIp(), dto.getPort());
                }

                existing
                        .map(entity -> {
                            statusMapper.updateEntity(entity, dto);
                            return statusRepository.save(entity);
                        })
                        .orElseGet(() -> statusRepository.save(statusMapper.toEntity(dto)));
                saved++;
            } catch (Exception exception) {
                log.error(STATUS_SAVE_ERROR_TEMPLATE, dto.getIp(), dto.getPort(), exception.getMessage());
            }
        }

        return saved;
    }
}
