package tn.iteam.adapter.observium;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tn.iteam.client.ObserviumClientX;
import tn.iteam.domain.ApiResponse;
import tn.iteam.domain.ObserviumProblem;
import tn.iteam.dto.ObserviumProblemDTO;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.exception.IntegrationDataUnavailableException;
import tn.iteam.mapper.ObserviumMapper;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.repository.ObserviumProblemRepository;
import tn.iteam.websocket.MonitoringWebSocketPublisher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ObserviumAdapter {

    private final ObserviumClientX observiumClient;
    private final ObserviumMapper observiumMapper;
    private final ObserviumProblemRepository problemRepository;
    private final MonitoringWebSocketPublisher monitoringWebSocketPublisher;

    private boolean isValid(ApiResponse<JsonNode> response) {
        return response != null
                && response.isSuccess()
                && response.getData() != null
                && response.getData().isArray();
    }

    public List<ServiceStatusDTO> fetchAll() {
        log.info("Fetching devices from Observium");
        ApiResponse<JsonNode> response = observiumClient.getDevices();
        List<ServiceStatusDTO> dtos = new ArrayList<>();
        if (!isValid(response)) {
            String errorMsg = response != null ? response.getMessage() : "null response";
            log.warn("Devices API failed: {}", errorMsg);
            throw IntegrationDataUnavailableException.forObservium("Devices unavailable: " + errorMsg);
        }
        for (JsonNode node : response.getData()) {
            dtos.add(observiumMapper.mapDeviceToDTO(node));
        }
        return dtos;
    }

    public List<ObserviumProblemDTO> fetchProblems() {
        log.info("Fetching alerts from Observium");
        ApiResponse<JsonNode> response = observiumClient.getAlerts();
        List<ObserviumProblemDTO> dtos = new ArrayList<>();
        if (!isValid(response)) {
            String errorMsg = response != null ? response.getMessage() : "null response";
            log.warn("Alerts API failed: {}", errorMsg);
            throw IntegrationDataUnavailableException.forObservium("Alerts unavailable: " + errorMsg);
        }
        for (JsonNode node : response.getData()) {
            dtos.add(observiumMapper.mapAlertToDTO(node));
        }
        return dtos;
    }

    public List<ObserviumProblemDTO> fetchProblemsAndSave() {
        log.info("Fetching + saving Observium problems");
        ApiResponse<JsonNode> response = observiumClient.getAlerts();

        List<ObserviumProblemDTO> dtos = new ArrayList<>();
        List<UnifiedMonitoringProblemDTO> monitoringProblems = new ArrayList<>();

        if (!isValid(response)) {
            log.warn("Alerts API failed: {}", response.getMessage());
            return dtos;
        }

        for (JsonNode node : response.getData()) {
            ObserviumProblemDTO dto = observiumMapper.mapAlertToDTO(node);
            dtos.add(dto);
            monitoringProblems.add(toUnifiedProblem(dto));
        }

        List<ObserviumProblem> entities = buildEntitiesToSave(dtos);
        entities.addAll(buildResolvedEntitiesToSave(dtos));

        if (!entities.isEmpty()) {
            problemRepository.saveAll(entities);
            log.info("{} problems saved", entities.size());
        }

        monitoringWebSocketPublisher.publishProblems(monitoringProblems);

        return dtos;
    }

    private List<ObserviumProblem> buildEntitiesToSave(List<ObserviumProblemDTO> dtos) {
        Map<String, ObserviumProblemDTO> activeByProblemId = new LinkedHashMap<>();

        for (ObserviumProblemDTO dto : dtos) {
            if (dto.isActive() && dto.getProblemId() != null && !dto.getProblemId().isBlank()) {
                activeByProblemId.put(dto.getProblemId(), dto);
            }
        }

        if (activeByProblemId.isEmpty()) {
            return List.of();
        }

        Map<String, ObserviumProblem> existingByProblemId = new LinkedHashMap<>();
        for (ObserviumProblem existing : problemRepository.findByProblemIdIn(activeByProblemId.keySet())) {
            existingByProblemId.put(existing.getProblemId(), existing);
        }

        List<ObserviumProblem> entities = new ArrayList<>();
        for (ObserviumProblemDTO dto : activeByProblemId.values()) {
            ObserviumProblem entity = existingByProblemId.getOrDefault(dto.getProblemId(), new ObserviumProblem());
            entity.setProblemId(dto.getProblemId());
            entity.setHostId(dto.getHostId());
            entity.setDevice(dto.getHost());
            entity.setDescription(dto.getDescription());
            entity.setSeverity(dto.getSeverity());
            entity.setActive(true);
            entity.setSource(dto.getSource());
            entity.setEventId(dto.getEventId());
            entities.add(entity);
        }

        return entities;
    }

    private List<ObserviumProblem> buildResolvedEntitiesToSave(List<ObserviumProblemDTO> dtos) {
        Map<String, ObserviumProblemDTO> byProblemId = new LinkedHashMap<>();

        for (ObserviumProblemDTO dto : dtos) {
            if (dto.getProblemId() != null && !dto.getProblemId().isBlank()) {
                byProblemId.put(dto.getProblemId(), dto);
            }
        }

        List<ObserviumProblem> resolvedEntities = new ArrayList<>();
        for (ObserviumProblem existing : problemRepository.findBySourceAndActiveTrue("OBSERVIUM")) {
            ObserviumProblemDTO dto = byProblemId.get(existing.getProblemId());
            if (dto != null) {
                existing.setHostId(dto.getHostId());
                existing.setDevice(dto.getHost());
                existing.setDescription(dto.getDescription());
                existing.setSeverity(dto.getSeverity());
                existing.setActive(dto.isActive());
                existing.setSource(dto.getSource());
                existing.setEventId(dto.getEventId());
            } else {
                existing.setActive(false);
            }
            resolvedEntities.add(existing);
        }

        return resolvedEntities;
    }

    private UnifiedMonitoringProblemDTO toUnifiedProblem(ObserviumProblemDTO dto) {
        String hostName = dto.getHost() != null && !dto.getHost().isBlank() ? dto.getHost() : "UNKNOWN";
        String hostId = dto.getHostId() != null ? dto.getHostId() : hostName;

        return UnifiedMonitoringProblemDTO.builder()
                .id(MonitoringSourceType.OBSERVIUM + ":" + dto.getProblemId())
                .source(MonitoringSourceType.OBSERVIUM)
                .problemId(dto.getProblemId())
                .eventId(dto.getEventId())
                .hostId(hostId)
                .hostName(hostName)
                .description(dto.getDescription())
                .severity(dto.getSeverity())
                .active(dto.isActive())
                .status(dto.isActive() ? "ACTIVE" : "RESOLVED")
                .build();
    }
}
