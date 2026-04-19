package tn.iteam.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.domain.ZabbixProblem;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.mapper.ZabbixProblemMapper;
import tn.iteam.repository.ZabbixProblemRepository;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.ZabbixDataQualityService;
import tn.iteam.service.ZabbixProblemService;
import tn.iteam.service.support.IntegrationExecutionHelper;
import tn.iteam.service.support.ZabbixProblemSanitizer;
import tn.iteam.util.MonitoringConstants;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ZabbixProblemServiceImpl implements ZabbixProblemService {

    private static final Logger log = LoggerFactory.getLogger(ZabbixProblemServiceImpl.class);
    private static final List<String> EXPOSED_SEVERITIES = List.of("4", "5");
    private static final String FETCHING_ACTIVE_PROBLEMS_MESSAGE = "Fetching active Zabbix problems";
    private static final String DUPLICATE_PROBLEM_LOG_TEMPLATE = "Duplicate problemId={} found in DB: {} rows";
    private static final String UNEXPECTED_ERROR_MESSAGE = "Unexpected error fetching Zabbix problems";
    private static final String SYNCHRONIZATION_FAILED_TEMPLATE = "Zabbix problems synchronization failed: {}";

    private final ZabbixAdapter zabbixAdapter;
    private final ZabbixProblemMapper mapper;
    private final ZabbixProblemRepository repository;
    private final SourceAvailabilityService availabilityService;
    private final ZabbixDataQualityService dataQualityService;
    private final IntegrationExecutionHelper executionHelper;
    private final ZabbixProblemSanitizer problemSanitizer;

    @Override
    public List<ZabbixProblemDTO> getPersistedFilteredActiveProblems() {
        return repository.findByActiveTrueAndSeverityIn(EXPOSED_SEVERITIES).stream()
                .map(mapper::toDTO)
                .toList();
    }

    @Override
    public List<ZabbixProblemDTO> synchronizeAndGetPersistedFilteredActiveProblems() {
        synchronizeActiveProblemsFromZabbix();
        List<ZabbixProblemDTO> persisted = getPersistedFilteredActiveProblems();

        if (!persisted.isEmpty() && !availabilityService.isAvailable(MonitoringConstants.SOURCE_ZABBIX)) {
            availabilityService.markDegraded(
                    MonitoringConstants.SOURCE_ZABBIX,
                    "Live Zabbix problems unavailable, using last persisted snapshot"
            );
        }

        return persisted;
    }

    @Override
    @Transactional
    public List<ZabbixProblemDTO> synchronizeActiveProblemsFromZabbix() {
        return synchronizeActiveProblemsFromZabbix(null);
    }

    @Override
    @Transactional
    public List<ZabbixProblemDTO> synchronizeActiveProblemsFromZabbix(JsonNode hosts) {
        log.info(FETCHING_ACTIVE_PROBLEMS_MESSAGE);
        return executionHelper.execute(
                availabilityService,
                log,
                MonitoringConstants.SOURCE_ZABBIX,
                MonitoringConstants.SOURCE_LABEL_ZABBIX,
                SYNCHRONIZATION_FAILED_TEMPLATE,
                UNEXPECTED_ERROR_MESSAGE,
                List.of(),
                () -> {
                    List<ZabbixProblemDTO> dtos = hosts == null
                            ? zabbixAdapter.fetchProblems()
                            : zabbixAdapter.fetchProblems(hosts);
                    List<ZabbixProblem> entitiesToSave = new ArrayList<>();
                    Set<String> liveProblemIds = new HashSet<>();

                    for (ZabbixProblemDTO dto : dtos) {
                        ZabbixProblemDTO sanitized = problemSanitizer.sanitize(dto, log);
                        if (sanitized == null) {
                            continue;
                        }

                        liveProblemIds.add(sanitized.getProblemId());

                        ZabbixProblem entity = mapper.toEntity(sanitized);
                        List<ZabbixProblem> existingList = repository.findAllByProblemId(entity.getProblemId()).stream()
                                .sorted(Comparator.comparing(ZabbixProblem::getId))
                                .toList();

                        if (!existingList.isEmpty()) {
                            ZabbixProblem existing = existingList.get(existingList.size() - 1);

                            existing.setHostId(entity.getHostId());
                            existing.setHost(entity.getHost());
                            existing.setDescription(entity.getDescription());
                            existing.setSeverity(entity.getSeverity());
                            existing.setActive(entity.getActive());
                            existing.setSource(entity.getSource());
                            existing.setEventId(entity.getEventId());
                            existing.setIp(entity.getIp());
                            existing.setPort(entity.getPort());
                            existing.setStartedAt(entity.getStartedAt());
                            existing.setResolvedAt(entity.getResolvedAt());
                            existing.setStatus(entity.getStatus());

                            if (existingList.size() > 1) {
                                log.warn(DUPLICATE_PROBLEM_LOG_TEMPLATE, entity.getProblemId(), existingList.size());
                            }
                            entitiesToSave.add(existing);
                        } else {
                            entitiesToSave.add(entity);
                        }
                    }

                    long resolvedAt = Instant.now().getEpochSecond();
                    for (ZabbixProblem persistedActive : repository.findByActiveTrue()) {
                        if (persistedActive.getProblemId() == null || liveProblemIds.contains(persistedActive.getProblemId())) {
                            continue;
                        }

                        persistedActive.setActive(false);
                        if (persistedActive.getResolvedAt() == null || persistedActive.getResolvedAt() == 0) {
                            persistedActive.setResolvedAt(resolvedAt);
                        }
                        persistedActive.setStatus(MonitoringConstants.STATUS_RESOLVED);
                        entitiesToSave.add(persistedActive);
                    }

                    List<ZabbixProblem> saved = repository.saveAll(entitiesToSave);
                    dataQualityService.logProblemQualitySummary(saved);
                    log.info("Saved {} Zabbix problems, active in live feed={}", saved.size(), liveProblemIds.size());
                    return dtos;
                }
        );
    }
}
