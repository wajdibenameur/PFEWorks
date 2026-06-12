package tn.iteam.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.domain.ZabbixProblem;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.mapper.ZabbixProblemMapper;
import tn.iteam.repository.ZabbixProblemRepository;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.TicketService;
import tn.iteam.service.ZabbixDataQualityService;
import tn.iteam.service.ZabbixProblemService;
import tn.iteam.service.support.IntegrationExecutionHelper;
import tn.iteam.service.support.DatabasePersistenceGuard;
import tn.iteam.service.support.MonitoringProblemNotificationService;
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
    private static final String FETCHING_ACTIVE_PROBLEMS_MESSAGE = "Fetching active Zabbix problems";
    private static final String DUPLICATE_PROBLEM_LOG_TEMPLATE = "Duplicate problemId={} found in DB: {} rows";
    private static final String UNEXPECTED_ERROR_MESSAGE = "Unexpected error fetching Zabbix problems";
    private static final String SYNCHRONIZATION_FAILED_TEMPLATE = "{} problems synchronization failed: {}";
    private static final String RECEIVED_PROBLEMS_LOG_TEMPLATE = "Received {} problems from Zabbix API";
    private static final String MAPPED_PROBLEMS_LOG_TEMPLATE = "Mapped {} problems, skipped {} invalid rows";
    private static final String PERSISTED_PROBLEMS_LOG_TEMPLATE = "Persisted {} rows into zabbix_problem";
    private static final String SKIPPED_PROBLEM_LOG_TEMPLATE = "Skipped problem eventId={} because hostId is null or invalid";

    private final ZabbixAdapter zabbixAdapter;
    private final ZabbixProblemMapper mapper;
    private final ZabbixProblemRepository repository;
    private final SourceAvailabilityService availabilityService;
    private final TicketService ticketService;
    private final ZabbixDataQualityService dataQualityService;
    private final IntegrationExecutionHelper executionHelper;
    private final ZabbixProblemSanitizer problemSanitizer;
    private final TransactionTemplate transactionTemplate;
    private final MonitoringProblemNotificationService monitoringProblemNotificationService;
    private final DatabasePersistenceGuard databasePersistenceGuard;

    @Override
    public List<ZabbixProblemDTO> getPersistedFilteredActiveProblems() {
        return repository.findByActiveTrue().stream()
                .map(mapper::toDTO)
                .toList();
    }

    @Override
    public List<ZabbixProblemDTO> getPersistedRecentProblems() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(
                        ZabbixProblem::getUpdatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .limit(5000)
                .map(mapper::toDTO)
                .toList();
    }

    @Override
    public List<ZabbixProblemDTO> synchronizeActiveProblems(List<ZabbixProblemDTO> problems) {
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
                    databasePersistenceGuard.safeSaveProblems(
                            MonitoringConstants.SOURCE_ZABBIX,
                            problems,
                            () -> executeInTransaction(() -> persistProblems(problems))
                    );
                    return problems;
                }
        );
    }

    @Override
    public List<ZabbixProblemDTO> synchronizeActiveProblemsFromZabbix() {
        return synchronizeActiveProblemsFromZabbix(null);
    }

    @Override
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
                    List<ZabbixProblemDTO> fetched =
                            hosts == null ? zabbixAdapter.fetchProblems() : zabbixAdapter.fetchProblems(hosts);
                    databasePersistenceGuard.safeSaveProblems(
                            MonitoringConstants.SOURCE_ZABBIX,
                            fetched,
                            () -> executeInTransaction(() -> persistProblems(fetched))
                    );
                    return fetched;
                }
        );
    }

    private List<ZabbixProblemDTO> executeInTransaction(java.util.concurrent.Callable<List<ZabbixProblemDTO>> action) {
        List<ZabbixProblemDTO> result = transactionTemplate.execute(status -> {
            try {
                return action.call();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
        return result == null ? List.of() : result;
    }

    private List<ZabbixProblemDTO> persistProblems(List<ZabbixProblemDTO> dtos) {
        log.info(RECEIVED_PROBLEMS_LOG_TEMPLATE, dtos.size());
        List<ZabbixProblem> entitiesToSave = new ArrayList<>();
        Set<String> liveProblemIds = new HashSet<>();
        int skippedInvalidRows = 0;

        for (ZabbixProblemDTO dto : dtos) {
            ZabbixProblemDTO sanitized = problemSanitizer.sanitize(dto, log);
            if (sanitized == null) {
                skippedInvalidRows++;
                continue;
            }

            if (Boolean.TRUE.equals(sanitized.getActive()) && shouldAutoCreateTicket(sanitized.getSeverity())) {
                try {
                    ticketService.createFromProblem(sanitized);
                } catch (Exception exception) {
                    log.warn("Unable to create ticket for Zabbix problem {}: {}", sanitized.getProblemId(), exception.getMessage(), exception);
                }
            }

            liveProblemIds.add(sanitized.getProblemId());

            ZabbixProblem entity = mapper.toEntity(sanitized);
            if (entity.getHostId() == null) {
                skippedInvalidRows++;
                log.warn(SKIPPED_PROBLEM_LOG_TEMPLATE, sanitized.getEventId());
                continue;
            }

            List<ZabbixProblem> existingList = repository.findAllByProblemId(entity.getProblemId()).stream()
                    .sorted(Comparator.comparing(ZabbixProblem::getId))
                    .toList();

            if (!existingList.isEmpty()) {
                ZabbixProblem existing = existingList.get(existingList.size() - 1);
                boolean reactivated = !Boolean.TRUE.equals(existing.getActive()) && Boolean.TRUE.equals(entity.getActive());
                if (reactivated) {
                    maybeNotifyProblem(sanitized, true);
                } else if (Boolean.TRUE.equals(existing.getActive()) && Boolean.TRUE.equals(entity.getActive())) {
                    maybeNotifyProblemReminder(existing, sanitized);
                }

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
                maybeNotifyProblem(sanitized, false);
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
            maybeNotifyProblemResolved(persistedActive, resolvedAt);
            entitiesToSave.add(persistedActive);
        }

        log.info(MAPPED_PROBLEMS_LOG_TEMPLATE, entitiesToSave.size(), skippedInvalidRows);

        if (entitiesToSave.isEmpty()) {
            log.warn(PERSISTED_PROBLEMS_LOG_TEMPLATE, 0);
            return dtos;
        }

        List<ZabbixProblem> saved = repository.saveAll(entitiesToSave);
        repository.flush();
        dataQualityService.logProblemQualitySummary(saved);
        log.info(PERSISTED_PROBLEMS_LOG_TEMPLATE, saved.size());
        log.info("Saved {} Zabbix problems, active in live feed={}", saved.size(), liveProblemIds.size());
        return dtos;
    }

    private boolean shouldAutoCreateTicket(String severity) {
        if (severity == null || severity.isBlank()) {
            return false;
        }

        try {
            return Integer.parseInt(severity.trim()) > 3;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private void maybeNotifyProblem(ZabbixProblemDTO dto, boolean reactivated) {
        if (dto == null || !Boolean.TRUE.equals(dto.getActive())) {
            return;
        }
        monitoringProblemNotificationService.notifySuperadminsForProblem(
                dto.getSource(),
                dto.getProblemId(),
                dto.getDescription(),
                dto.getSeverity(),
                resolveResourceRef(dto),
                dto.getStartedAt(),
                reactivated
        );
    }

    private void maybeNotifyProblemReminder(ZabbixProblem existing, ZabbixProblemDTO dto) {
        if (existing == null || dto == null || !Boolean.TRUE.equals(dto.getActive())) {
            return;
        }
        monitoringProblemNotificationService.notifySuperadminsForProblemReminder(
                dto.getSource(),
                dto.getProblemId(),
                dto.getDescription(),
                dto.getSeverity(),
                resolveResourceRef(dto),
                existing.getStartedAt(),
                Instant.now().getEpochSecond()
        );
    }

    private void maybeNotifyProblemResolved(ZabbixProblem problem, long resolvedAt) {
        if (problem == null) {
            return;
        }
        monitoringProblemNotificationService.notifySuperadminsForProblemResolved(
                problem.getSource(),
                problem.getProblemId(),
                problem.getDescription(),
                problem.getSeverity(),
                resolveResourceRef(problem),
                problem.getStartedAt(),
                resolvedAt
        );
    }

    private String resolveResourceRef(ZabbixProblemDTO dto) {
        if (dto == null) {
            return null;
        }
        if (dto.getHost() != null && !dto.getHost().isBlank()) {
            return dto.getHost();
        }
        if (dto.getIp() != null && !dto.getIp().isBlank()) {
            return dto.getIp();
        }
        return dto.getHostId();
    }

    private String resolveResourceRef(ZabbixProblem problem) {
        if (problem == null) {
            return null;
        }
        if (problem.getHost() != null && !problem.getHost().isBlank()) {
            return problem.getHost();
        }
        if (problem.getIp() != null && !problem.getIp().isBlank()) {
            return problem.getIp();
        }
        return problem.getHostId() != null ? problem.getHostId().toString() : null;
    }
}
