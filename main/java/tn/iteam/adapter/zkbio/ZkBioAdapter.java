package tn.iteam.adapter.zkbio;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.iteam.client.ZkBioClient;
import tn.iteam.domain.ZkBioProblem;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.mapper.ZkBioMapper;
import tn.iteam.repository.ZkBioProblemRepository;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ZkBioAdapter {

    private static final Logger log = LoggerFactory.getLogger(ZkBioAdapter.class);

    private final ZkBioClient zkBioClient;
    private final ZkBioMapper zkBioMapper;
    private final ZkBioProblemRepository problemRepository;

    /**
     * Vérifie l'état du serveur ZKBio
     */
    public List<ServiceStatusDTO> fetchAll() {
        log.info(" Checking ZKBio server");

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
                log.info(" ZKBio Server is UP");
            } else {
                log.warn(" ZKBio Server devices API returned null");
            }
        } catch (Exception e) {
            log.error(" Error connecting to ZKBio Server", e);
        }

        dtos.add(dto);
        return dtos;
    }

    /**
     * Récupère les alertes/problèmes depuis ZKBio et les transforme en DTO
     */
    public List<ZkBioProblemDTO> fetchProblems() {
        log.info(" Fetching problems from ZKBio");

        JsonNode alerts = zkBioClient.getAlerts();
        List<ZkBioProblemDTO> dtos = new ArrayList<>();

        if (alerts == null || !alerts.isArray()) {
            log.warn("No alerts received from ZKBio");
            return dtos;
        }

        for (JsonNode alertNode : alerts) {
            dtos.add(zkBioMapper.mapAlertToDTO(alertNode));
        }

        log.info(" {} problems fetched from ZKBio", dtos.size());
        return dtos;
    }

    /**
     * Récupère les alertes et les enregistre dans la base (uniquement les actifs)
     */
    public List<ZkBioProblemDTO> fetchProblemsAndSave() {
        log.info(" Fetching problems from ZKBio and saving to DB");

        JsonNode alerts = zkBioClient.getAlerts();
        List<ZkBioProblemDTO> dtos = new ArrayList<>();
        List<ZkBioProblem> entities = new ArrayList<>();

        if (alerts == null || !alerts.isArray()) {
            log.warn("No alerts received from ZKBio");
            return dtos;
        }

        for (JsonNode alertNode : alerts) {
            ZkBioProblemDTO dto = zkBioMapper.mapAlertToDTO(alertNode);
            dtos.add(dto);

            // On ne garde que les problèmes actifs
            if(dto.isActive()) {
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

        if(!entities.isEmpty()) {
            problemRepository.saveAll(entities);
            log.info(" {} problems saved to ZKBio database", entities.size());
        }

        return dtos;
    }
}
