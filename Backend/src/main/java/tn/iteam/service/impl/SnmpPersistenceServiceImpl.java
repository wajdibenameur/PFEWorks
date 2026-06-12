package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.domain.SnmpMetric;
import tn.iteam.domain.SnmpProblem;
import tn.iteam.dto.SnmpMetricDTO;
import tn.iteam.dto.SnmpProblemDTO;
import tn.iteam.mapper.SnmpMetricMapper;
import tn.iteam.repository.SnmpMetricRepository;
import tn.iteam.repository.SnmpProblemRepository;
import tn.iteam.service.SnmpPersistenceService;
import tn.iteam.service.TicketService;
import tn.iteam.service.support.MonitoringProblemNotificationService;
import tn.iteam.util.MonitoringConstants;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnmpPersistenceServiceImpl implements SnmpPersistenceService {

    private static final String EMPTY_PROBLEM_ID_LOG_TEMPLATE = "Skipping SNMP problem without problemId: {}";
    private static final String EMPTY_METRIC_LOG_TEMPLATE = "Skipping SNMP metric with empty hostId/itemId/timestamp: {}";
    private static final String DUPLICATE_PROBLEM_LOG_TEMPLATE = "Duplicate SNMP problemId={} found in DB: {} rows";

    private final SnmpProblemRepository problemRepository;
    private final SnmpMetricRepository metricRepository;
    private final TicketService ticketService;
    private final SnmpMetricMapper metricMapper;
    private final MonitoringProblemNotificationService monitoringProblemNotificationService;

    @Override
    @Transactional
    public int saveProblems(List<SnmpProblemDTO> problems) {
        if (problems == null || problems.isEmpty()) {
            return 0;
        }

        List<SnmpProblem> entitiesToSave = new ArrayList<>();
        Set<String> liveProblemIds = new HashSet<>();

        for (SnmpProblemDTO dto : problems) {
            if (dto.getProblemId() == null || dto.getProblemId().isBlank()) {
                log.warn(EMPTY_PROBLEM_ID_LOG_TEMPLATE, dto);
                continue;
            }

            if (dto.isActive() && shouldAutoCreateTicket(dto.getSeverity())) {
                try {
                    ticketService.createFromProblem(dto);
                } catch (Exception exception) {
                    log.warn("Unable to create ticket for SNMP problem {}: {}", dto.getProblemId(), exception.getMessage(), exception);
                }
            }

            liveProblemIds.add(dto.getProblemId());
            SnmpProblem incoming = toProblemEntity(dto);
            List<SnmpProblem> existingList = problemRepository.findAllByProblemId(dto.getProblemId()).stream()
                    .sorted(Comparator.comparing(SnmpProblem::getId))
                    .toList();

            if (existingList.isEmpty()) {
                maybeNotifyProblem(dto, false);
                entitiesToSave.add(incoming);
                continue;
            }

            SnmpProblem existing = existingList.get(existingList.size() - 1);
            boolean reactivated = !Boolean.TRUE.equals(existing.getActive()) && Boolean.TRUE.equals(incoming.getActive());
            if (reactivated) {
                maybeNotifyProblem(dto, true);
            } else if (Boolean.TRUE.equals(existing.getActive()) && Boolean.TRUE.equals(incoming.getActive())) {
                maybeNotifyProblemReminder(existing, incoming);
            }
            existing.setProblemId(incoming.getProblemId());
            existing.setHostId(incoming.getHostId());
            existing.setDevice(incoming.getDevice());
            existing.setDescription(incoming.getDescription());
            existing.setSeverity(incoming.getSeverity());
            existing.setActive(incoming.getActive());
            existing.setSource(incoming.getSource());
            existing.setEventId(incoming.getEventId());
            existing.setStartedAt(existing.getStartedAt() == null || reactivated ? incoming.getStartedAt() : existing.getStartedAt());
            existing.setLastObservedAt(incoming.getLastObservedAt());
            existing.setResolvedAt(incoming.getResolvedAt());
            entitiesToSave.add(existing);

            if (existingList.size() > 1) {
                log.warn(DUPLICATE_PROBLEM_LOG_TEMPLATE, dto.getProblemId(), existingList.size());
            }
        }

        long resolvedAt = Instant.now().getEpochSecond();
        for (SnmpProblem persistedActive : problemRepository.findByActiveTrue()) {
            if (persistedActive.getProblemId() == null || liveProblemIds.contains(persistedActive.getProblemId())) {
                continue;
            }

            persistedActive.setActive(false);
            if (persistedActive.getResolvedAt() == null || persistedActive.getResolvedAt() == 0L) {
                persistedActive.setResolvedAt(resolvedAt);
            }
            maybeNotifyProblemResolved(persistedActive, resolvedAt);
            entitiesToSave.add(persistedActive);
        }

        return problemRepository.saveAll(entitiesToSave).size();
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

    @Override
    @Transactional
    public int saveMetrics(List<SnmpMetricDTO> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return 0;
        }

        List<SnmpMetric> entitiesToSave = new ArrayList<>();

        for (SnmpMetricDTO dto : metrics) {
            if (dto.getHostId() == null || dto.getHostId().isBlank()
                    || dto.getItemId() == null || dto.getItemId().isBlank()
                    || dto.getTimestamp() == null) {
                log.warn(EMPTY_METRIC_LOG_TEMPLATE, dto);
                continue;
            }

            SnmpMetric incoming = metricMapper.toEntity(dto);
            SnmpMetric entity = metricRepository.findByHostIdAndItemIdAndTimestamp(
                            dto.getHostId(),
                            dto.getItemId(),
                            dto.getTimestamp()
                    )
                    .map(existing -> {
                        existing.setHostName(incoming.getHostName());
                        existing.setMetricKey(incoming.getMetricKey());
                        existing.setValue(incoming.getValue());
                        existing.setTimestamp(incoming.getTimestamp());
                        existing.setIp(incoming.getIp());
                        existing.setPort(incoming.getPort());
                        return existing;
                    })
                    .orElse(incoming);

            entitiesToSave.add(entity);
        }

        return metricRepository.saveAll(entitiesToSave).size();
    }

    private SnmpProblem toProblemEntity(SnmpProblemDTO dto) {
        return SnmpProblem.builder()
                .problemId(dto.getProblemId())
                .hostId(dto.getHostId())
                .device(dto.getHost())
                .description(dto.getDescription())
                .severity(dto.getSeverity())
                .active(dto.isActive())
                .source(MonitoringConstants.SOURCE_SNMP)
                .eventId(dto.getEventId())
                .startedAt(dto.getStartedAt())
                .lastObservedAt(dto.getLastObservedAt())
                .resolvedAt(dto.getResolvedAt())
                .build();
    }

    private void maybeNotifyProblem(SnmpProblemDTO dto, boolean reactivated) {
        if (dto == null || !dto.isActive()) {
            return;
        }
        monitoringProblemNotificationService.notifySuperadminsForProblem(
                dto.getSource(),
                dto.getProblemId(),
                dto.getDescription(),
                dto.getSeverity(),
                dto.getHost(),
                dto.getStartedAt(),
                reactivated
        );
    }

    private void maybeNotifyProblemReminder(SnmpProblem existing, SnmpProblem incoming) {
        if (existing == null || incoming == null || !Boolean.TRUE.equals(incoming.getActive())) {
            return;
        }
        monitoringProblemNotificationService.notifySuperadminsForProblemReminder(
                incoming.getSource(),
                incoming.getProblemId(),
                incoming.getDescription(),
                incoming.getSeverity(),
                incoming.getDevice(),
                existing.getStartedAt(),
                incoming.getLastObservedAt()
        );
    }

    private void maybeNotifyProblemResolved(SnmpProblem problem, long resolvedAt) {
        if (problem == null) {
            return;
        }
        monitoringProblemNotificationService.notifySuperadminsForProblemResolved(
                problem.getSource(),
                problem.getProblemId(),
                problem.getDescription(),
                problem.getSeverity(),
                problem.getDevice(),
                problem.getStartedAt(),
                resolvedAt
        );
    }
}
