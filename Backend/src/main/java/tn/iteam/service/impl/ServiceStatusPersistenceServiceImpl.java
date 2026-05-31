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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceStatusPersistenceServiceImpl implements ServiceStatusPersistenceService {

    private static final String STATUS_SAVE_ERROR_TEMPLATE = "Error saving status for {}:{} - {}";
    private static final int PERSISTENCE_CHUNK_SIZE = 500;

    private final ServiceStatusRepository statusRepository;
    private final ServiceStatusMapper statusMapper;

    @Override
    @Transactional
    public int saveAll(List<ServiceStatusDTO> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return 0;
        }

        long startedAt = System.currentTimeMillis();
        String source = statuses.get(0).getSource();
        List<ServiceStatus> existingForSource = source == null ? List.of() : statusRepository.findBySource(source);

        Map<String, ServiceStatus> existingByIdentity = existingForSource.stream()
                .filter(entity -> identityKey(entity.getSource(), entity.getName(), entity.getIp(), entity.getPort()) != null)
                .collect(Collectors.toMap(
                        entity -> identityKey(entity.getSource(), entity.getName(), entity.getIp(), entity.getPort()),
                        Function.identity(),
                        (left, right) -> left
                ));

        List<ServiceStatus> entitiesToSave = statuses.stream().map(dto -> {
            try {
                String key = identityKey(dto.getSource(), dto.getName(), dto.getIp(), dto.getPort());
                ServiceStatus existing = key == null ? null : existingByIdentity.get(key);
                if (existing != null) {
                    statusMapper.updateEntity(existing, dto);
                    return existing;
                }
                return statusMapper.toEntity(dto);
            } catch (Exception exception) {
                log.error(STATUS_SAVE_ERROR_TEMPLATE, dto.getIp(), dto.getPort(), exception.getMessage());
                return null;
            }
        }).filter(java.util.Objects::nonNull).toList();

        int saved = 0;
        for (int index = 0; index < entitiesToSave.size(); index += PERSISTENCE_CHUNK_SIZE) {
            int end = Math.min(index + PERSISTENCE_CHUNK_SIZE, entitiesToSave.size());
            saved += statusRepository.saveAll(entitiesToSave.subList(index, end)).size();
        }

        log.info("ServiceStatus batch persistence source={} rows={} durationMs={}",
                source, saved, System.currentTimeMillis() - startedAt);
        return saved;
    }

    private String identityKey(String source, String name, String ip, Integer port) {
        if (source == null || source.isBlank() || ip == null || ip.isBlank()) {
            return null;
        }
        String normalizedSource = source.trim().toUpperCase();
        String normalizedIp = ip.trim();
        if (port != null) {
            return normalizedSource + "|" + normalizedIp + "|" + port;
        }
        String normalizedName = name == null ? "" : name.trim();
        return normalizedSource + "|" + normalizedIp + "|" + normalizedName;
    }
}
