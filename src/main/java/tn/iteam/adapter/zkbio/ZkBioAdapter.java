package tn.iteam.adapter.zkbio;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.iteam.client.ZkBioClientX;
import tn.iteam.domain.ZkBioProblem;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.exception.IntegrationException;
import tn.iteam.mapper.ZkBioMapper;
import tn.iteam.repository.ZkBioProblemRepository;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ZkBioAdapter {

    private static final Logger log = LoggerFactory.getLogger(ZkBioAdapter.class);

    private final ZkBioClientX zkBioClient;
    private final ZkBioMapper zkBioMapper;
    private final ZkBioProblemRepository problemRepository;

    public List<ServiceStatusDTO> fetchAll() {
        log.info("Checking ZKBio server");

        List<ServiceStatusDTO> dtos = new ArrayList<>();
        ServiceStatusDTO dto = new ServiceStatusDTO();
        dto.setSource("ZKBIO");
        dto.setName("ZKBio Server");
        dto.setIp("192.168.11.8");
        dto.setPort(8098);
        dto.setProtocol("HTTPS");
        dto.setCategory("ACCESS");
        dto.setStatus("DOWN");

        try {
            if (zkBioClient.getDevices() != null) {
                dto.setStatus("UP");
                log.info("ZKBio Server is UP");
            } else {
                log.warn("ZKBio Server devices API returned null");
            }
        } catch (IntegrationException e) {
            log.warn("ZKBio unavailable: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error connecting to ZKBio Server", e);
        }

        dtos.add(dto);
        return dtos;
    }

    public List<ZkBioProblemDTO> fetchProblems() {
        log.info("Fetching problems from ZKBio");

        List<ZkBioProblemDTO> dtos = new ArrayList<>();
        JsonNode alerts;
        try {
            alerts = zkBioClient.getAlerts();
        } catch (IntegrationException e) {
            log.warn("ZKBio problems unavailable: {}", e.getMessage());
            return dtos;
        }

        if (alerts == null || !alerts.isArray()) {
            log.warn("No alerts received from ZKBio");
            return dtos;
        }

        for (JsonNode alertNode : alerts) {
            dtos.add(zkBioMapper.mapAlertToDTO(alertNode));
        }

        log.info("{} problems fetched from ZKBio", dtos.size());
        return dtos;
    }

    public List<ZkBioProblemDTO> fetchProblemsAndSave() {
        log.info("Fetching problems from ZKBio and saving to DB");

        List<ZkBioProblemDTO> dtos = new ArrayList<>();
        List<ZkBioProblem> entities = new ArrayList<>();
        JsonNode alerts;
        try {
            alerts = zkBioClient.getAlerts();
        } catch (IntegrationException e) {
            log.warn("ZKBio problems unavailable during persistence: {}", e.getMessage());
            return dtos;
        }

        if (alerts == null || !alerts.isArray()) {
            log.warn("No alerts received from ZKBio");
            return dtos;
        }

        for (JsonNode alertNode : alerts) {
            ZkBioProblemDTO dto = zkBioMapper.mapAlertToDTO(alertNode);
            dtos.add(dto);

            if (dto.isActive()) {
                ZkBioProblem entity = ZkBioProblem.builder()
                        .problemId(dto.getProblemId())
                        .device(dto.getHost())
                        .description(dto.getDescription())
                        .active(dto.isActive())
                        .source(dto.getSource())
                        .eventId(dto.getEventId())
                        .build();
                entities.add(entity);
            }
        }

        if (!entities.isEmpty()) {
            problemRepository.saveAll(entities);
            log.info("{} problems saved to ZKBio database", entities.size());
        }

        return dtos;
    }
}
