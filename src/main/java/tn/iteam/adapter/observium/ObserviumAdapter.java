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
import tn.iteam.mapper.ObserviumMapper;
import tn.iteam.repository.ObserviumProblemRepository;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ObserviumAdapter {

    private final ObserviumClientX observiumClient;
    private final ObserviumMapper observiumMapper;
    private final ObserviumProblemRepository problemRepository;

    // ================= UTIL =================
    private boolean isValid(ApiResponse<JsonNode> response) {
        return response != null
                && response.isSuccess()
                && response.getData() != null
                && response.getData().isArray();
    }

    // ================= DEVICES =================
    public List<ServiceStatusDTO> fetchAll() {

        log.info("Fetching devices from Observium");

        ApiResponse<JsonNode> response = observiumClient.getDevices();

        List<ServiceStatusDTO> dtos = new ArrayList<>();

        if (!isValid(response)) {
            log.warn("Devices API failed: {}", response.getMessage());
            return dtos;
        }

        for (JsonNode node : response.getData()) {
            dtos.add(observiumMapper.mapDeviceToDTO(node));
        }

        return dtos;
    }

    // ================= ALERTS =================
    public List<ObserviumProblemDTO> fetchProblems() {

        log.info("Fetching alerts from Observium");

        ApiResponse<JsonNode> response = observiumClient.getAlerts();

        List<ObserviumProblemDTO> dtos = new ArrayList<>();

        if (!isValid(response)) {
            log.warn("Alerts API failed: {}", response.getMessage());
            return dtos;
        }

        for (JsonNode node : response.getData()) {
            dtos.add(observiumMapper.mapAlertToDTO(node));
        }

        return dtos;
    }

    // ================= FETCH + SAVE =================
    public List<ObserviumProblemDTO> fetchProblemsAndSave() {

        log.info("Fetching + saving Observium problems");

        ApiResponse<JsonNode> response = observiumClient.getAlerts();

        List<ObserviumProblemDTO> dtos = new ArrayList<>();
        List<ObserviumProblem> entities = new ArrayList<>();

        if (!isValid(response)) {
            log.warn("Alerts API failed: {}", response.getMessage());
            return dtos;
        }

        for (JsonNode node : response.getData()) {

            ObserviumProblemDTO dto = observiumMapper.mapAlertToDTO(node);
            dtos.add(dto);

            if (dto.isActive()) {
                entities.add(ObserviumProblem.builder()
                        .problemId(dto.getProblemId())
                        .device(dto.getHost())
                        .description(dto.getDescription())
                        .severity(dto.getSeverity())
                        .active(true)
                        .source(dto.getSource())
                        .eventId(dto.getEventId())
                        .build());
            }
        }

        if (!entities.isEmpty()) {
            problemRepository.saveAll(entities);
            log.info("{} problems saved", entities.size());
        }

        return dtos;
    }
}