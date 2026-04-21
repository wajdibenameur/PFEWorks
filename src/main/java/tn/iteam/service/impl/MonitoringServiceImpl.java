package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
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
import tn.iteam.repository.ServiceStatusRepository;
import tn.iteam.service.MonitoringService;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.support.IntegrationExecutionHelper;
import tn.iteam.util.MonitoringConstants;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MonitoringServiceImpl implements MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringServiceImpl.class);
    private static final String CAMERA_SUBNET = "192.168.11";
    private static final String STATUS_SAVE_ERROR_TEMPLATE = "Error saving status for {}:{} - {}";
    private static final String SKIP_COLLECTION_LOG_TEMPLATE =
            "Skipping {} collection because source is marked unavailable and retry backoff ({} ms) is still active";

    private final ZabbixAdapter zabbixAdapter;
    private final ObserviumAdapter observiumAdapter;
    private final CameraAdapter cameraAdapter;
    private final ZkBioAdapter zkbioAdapter;

    private final ServiceStatusRepository statusRepository;
    private final ServiceStatusMapper statusMapper;
    @Qualifier("taskExecutor")
    private final TaskExecutor taskExecutor;
    private final SourceAvailabilityService availabilityService;
    private final IntegrationExecutionHelper executionHelper;

    @Value("${observium.retry-backoff-ms:120000}")
    private long observiumRetryBackoffMs;

    @Value("${zkbio.retry-backoff-ms:120000}")
    private long zkBioRetryBackoffMs;

    @Value("${camera.retry-backoff-ms:120000}")
    private long cameraRetryBackoffMs;

    @Override
    @Async
    public void collectAll() {
        taskExecutor.execute(this::collectZabbix);
        taskExecutor.execute(this::collectObservium);
        taskExecutor.execute(this::collectZkBio);
        taskExecutor.execute(this::collectCamera);
    }

    @Override
    @Async
    public void collectZabbix() {
        collect(
                MonitoringConstants.SOURCE_ZABBIX,
                MonitoringConstants.SOURCE_LABEL_ZABBIX,
                () -> {
                    List<ServiceStatusDTO> dtos = zabbixAdapter.fetchAll();
                    saveOrUpdateStatus(dtos);
                    log.info("Collected {} Zabbix items", dtos.size());
                }
        );
    }

    @Override
    @Async
    public void collectObservium() {
        if (shouldSkipCollection(MonitoringConstants.SOURCE_OBSERVIUM, observiumRetryBackoffMs, MonitoringConstants.SOURCE_LABEL_OBSERVIUM)) {
            return;
        }

        collect(
                MonitoringConstants.SOURCE_OBSERVIUM,
                MonitoringConstants.SOURCE_LABEL_OBSERVIUM,
                () -> {
                    List<ServiceStatusDTO> dtos = observiumAdapter.fetchAll();
                    saveOrUpdateStatus(dtos);

                    List<ObserviumProblemDTO> problems = observiumAdapter.fetchProblemsAndSave();
                    int metrics = observiumAdapter.fetchMetricsAndSave().size();
                    log.info("Collected {} Observium items, {} problems, {} metrics", dtos.size(), problems.size(), metrics);
                }
        );
    }

    @Override
    @Async
    public void collectObserviumHosts() {
        if (shouldSkipCollection(MonitoringConstants.SOURCE_OBSERVIUM, observiumRetryBackoffMs, MonitoringConstants.SOURCE_LABEL_OBSERVIUM)) {
            return;
        }

        collect(
                MonitoringConstants.SOURCE_OBSERVIUM,
                MonitoringConstants.SOURCE_LABEL_OBSERVIUM,
                () -> {
                    List<ServiceStatusDTO> dtos = observiumAdapter.fetchAll();
                    saveOrUpdateStatus(dtos);
                    int metrics = observiumAdapter.fetchMetricsAndSave().size();
                    log.info("Collected {} Observium hosts and {} metrics", dtos.size(), metrics);
                }
        );
    }

    @Override
    @Async
    public void collectZkBio() {
        if (shouldSkipCollection(MonitoringConstants.SOURCE_ZKBIO, zkBioRetryBackoffMs, MonitoringConstants.SOURCE_LABEL_ZKBIO)) {
            return;
        }

        collect(
                MonitoringConstants.SOURCE_ZKBIO,
                MonitoringConstants.SOURCE_LABEL_ZKBIO,
                () -> {
                    List<ServiceStatusDTO> dtos = zkbioAdapter.fetchAll();
                    saveOrUpdateStatus(dtos);

                    List<ZkBioProblemDTO> problems = zkbioAdapter.fetchProblemsAndSave();
                    int metrics = zkbioAdapter.fetchMetricsAndSave().size();
                    log.info("Collected {} ZKBio items, {} problems, {} metrics", dtos.size(), problems.size(), metrics);
                }
        );
    }

    @Override
    @Async
    public void collectCamera() {
        if (shouldSkipCollection(MonitoringConstants.SOURCE_CAMERA, cameraRetryBackoffMs, MonitoringConstants.SOURCE_LABEL_CAMERA)) {
            return;
        }

        collect(
                MonitoringConstants.SOURCE_CAMERA,
                MonitoringConstants.SOURCE_LABEL_CAMERA,
                () -> {
                    List<ServiceStatusDTO> dtos = cameraAdapter.fetchAll(CAMERA_SUBNET);
                    saveOrUpdateStatus(dtos);
                    log.info("Collected {} Camera items", dtos.size());
                }
        );
    }

    private void saveOrUpdateStatus(List<ServiceStatusDTO> dtos) {
        for (ServiceStatusDTO dto : dtos) {
            try {
                Optional<tn.iteam.domain.ServiceStatus> existing = Optional.empty();
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
                log.error(STATUS_SAVE_ERROR_TEMPLATE, dto.getIp(), dto.getPort(), e.getMessage());
            }
        }
    }

    private boolean shouldSkipCollection(String source, long retryBackoffMs, String sourceLabel) {
        if (availabilityService.shouldAttempt(source, retryBackoffMs)) {
            return false;
        }

        log.debug(SKIP_COLLECTION_LOG_TEMPLATE, sourceLabel, retryBackoffMs);
        return true;
    }

    private void collect(String source, String sourceLabel, CheckedRunnable action) {
        executionHelper.execute(
                availabilityService,
                log,
                source,
                sourceLabel,
                MonitoringConstants.COLLECTION_FAILED_TEMPLATE,
                MonitoringConstants.UNEXPECTED_COLLECTION_ERROR_TEMPLATE,
                action::run
        );
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run();
    }
}
