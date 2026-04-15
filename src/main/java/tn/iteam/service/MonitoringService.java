package tn.iteam.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.camera.CameraAdapter;
import tn.iteam.adapter.observium.ObserviumAdapter;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.adapter.zkbio.ZkBioAdapter;
import tn.iteam.dto.ObserviumProblemDTO;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.mapper.ServiceStatusMapper;
import tn.iteam.repository.ObserviumProblemRepository;
import tn.iteam.repository.ServiceStatusRepository;
import tn.iteam.repository.ZkBioProblemRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);

    private final ZabbixAdapter zabbixAdapter;
    private final ObserviumAdapter observiumAdapter;
    private final CameraAdapter cameraAdapter;
    private final ZkBioAdapter zkbioAdapter;

    private final ServiceStatusRepository statusRepository;
    private final ServiceStatusMapper statusMapper;

    private final ZkBioProblemRepository zkRepo;
    private final ObserviumProblemRepository obsRepo;

    @Async
    public void collectAll() {
        collectZabbix();
        collectObservium();
        collectZkBio();
        collectCamera();
    }

    @Async
    public void collectZabbix() {
        try {
            List<ServiceStatusDTO> dtos = zabbixAdapter.fetchAll();
            saveOrUpdateStatus(dtos);
            log.info("Collected {} Zabbix items", dtos.size());
        } catch (Exception e) {
            log.error("Error collecting Zabbix data", e);
        }
    }

    @Async
    public void collectObservium() {
        try {
            List<ServiceStatusDTO> dtos = observiumAdapter.fetchAll();
            saveOrUpdateStatus(dtos);

            List<ObserviumProblemDTO> problems = observiumAdapter.fetchProblemsAndSave();
            log.info("Collected {} Observium items, {} problems", dtos.size(), problems.size());
        } catch (Exception e) {
            log.error("Error collecting Observium data", e);
        }
    }

    @Async
    public void collectZkBio() {
        try {
            List<ServiceStatusDTO> dtos = zkbioAdapter.fetchAll();
            saveOrUpdateStatus(dtos);

            List<ZkBioProblemDTO> problems = zkbioAdapter.fetchProblemsAndSave();
            log.info("Collected {} ZKBio items, {} problems", dtos.size(), problems.size());
        } catch (Exception e) {
            log.error("Error collecting ZKBio data", e);
        }
    }

    @Async
    public void collectCamera() {
        try {
            List<ServiceStatusDTO> dtos = cameraAdapter.fetchAll("192.168.11");
            saveOrUpdateStatus(dtos);
            log.info("Collected {} Camera items", dtos.size());
        } catch (Exception e) {
            log.error("Error collecting Camera data", e);
        }
    }

    private void saveOrUpdateStatus(List<ServiceStatusDTO> dtos) {
        for (ServiceStatusDTO dto : dtos) {
            try {
                var existing = java.util.Optional.<tn.iteam.domain.ServiceStatus>empty();
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
            } catch (Exception e) {
                log.error("Error saving status for {}:{} - {}", dto.getIp(), dto.getPort(), e.getMessage());
            }
        }
    }
}
