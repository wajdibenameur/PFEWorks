package tn.iteam.adapter.observium;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.iteam.client.ObserviumClient;
import tn.iteam.domain.ObserviumProblem;
import tn.iteam.dto.ObserviumProblemDTO;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.mapper.ObserviumMapper;
import tn.iteam.repository.ObserviumProblemRepository;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ObserviumAdapter {

    private static final Logger log = LoggerFactory.getLogger(ObserviumAdapter.class);

    private final ObserviumClient observiumClient;
    private final ObserviumMapper observiumMapper;

    /**
     * Récupère tous les devices et les transforme en DTO
     */
    public List<ServiceStatusDTO> fetchAll() {
        log.info(" Fetching data from Observium");

        JsonNode devices = observiumClient.getDevices();
        List<ServiceStatusDTO> dtos = new ArrayList<>();

        if (devices == null || !devices.isArray()) {
            log.warn("No devices received from Observium");
            return dtos;
        }

        for (JsonNode deviceNode : devices) {
            dtos.add(observiumMapper.mapDeviceToDTO(deviceNode));
        }

        log.info(" {} devices fetched from Observium", dtos.size());
        return dtos;
    }
    public List<ObserviumProblemDTO> fetchProblems() {
        log.info(" Fetching problems from Observium");

        JsonNode alerts = observiumClient.getAlerts();
        List<ObserviumProblemDTO> dtos = new ArrayList<>();

        if (alerts == null || !alerts.isArray()) {
            log.warn("No alerts received from Observium");
            return dtos;
        }

        for (JsonNode alertNode : alerts) {
            dtos.add(observiumMapper.mapAlertToDTO(alertNode));
        }

        log.info(" {} problems fetched from Observium", dtos.size());
        return dtos;
    }

    private final ObserviumProblemRepository problemRepository; // à injecter

    public List<ObserviumProblemDTO> fetchProblemsAndSave() {
        log.info(" Fetching problems from Observium");

        JsonNode alerts = observiumClient.getAlerts();
        List<ObserviumProblemDTO> dtos = new ArrayList<>();
        List<ObserviumProblem> entities = new ArrayList<>();

        if (alerts == null || !alerts.isArray()) {
            log.warn("No alerts received from Observium");
            return dtos;
        }

        for (JsonNode alertNode : alerts) {
            ObserviumProblemDTO dto = observiumMapper.mapAlertToDTO(alertNode);
            dtos.add(dto);

            if(dto.isActive()) { // seulement les problèmes DOWN/ouverts
                ObserviumProblem entity = ObserviumProblem.builder()
                        .problemId(dto.getProblemId())
                        .device(dto.getHost())
                        .description(dto.getDescription())
                        .severity(dto.getSeverity())
                        .active(dto.isActive())
                        .source(dto.getSource())
                        .eventId(dto.getEventId())
                        .build();

                entities.add(entity);
            }
        }

        if(!entities.isEmpty()) {
            problemRepository.saveAll(entities);
            log.info(" {} problems saved to database", entities.size());
        }

        return dtos;
    }
}
