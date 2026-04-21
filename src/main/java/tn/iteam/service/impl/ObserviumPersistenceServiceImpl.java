package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.domain.ObserviumMetric;
import tn.iteam.domain.ObserviumProblem;
import tn.iteam.dto.ObserviumMetricDTO;
import tn.iteam.dto.ObserviumProblemDTO;
import tn.iteam.mapper.ObserviumMetricMapper;
import tn.iteam.repository.ObserviumMetricRepository;
import tn.iteam.repository.ObserviumProblemRepository;
import tn.iteam.service.ObserviumPersistenceService;
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
public class ObserviumPersistenceServiceImpl implements ObserviumPersistenceService {

    private static final String EMPTY_PROBLEM_ID_LOG_TEMPLATE = "Skipping Observium problem without problemId: {}";
    private static final String EMPTY_METRIC_LOG_TEMPLATE = "Skipping Observium metric with empty hostId/itemId/timestamp: {}";
    private static final String DUPLICATE_PROBLEM_LOG_TEMPLATE = "Duplicate Observium problemId={} found in DB: {} rows";

    private final ObserviumProblemRepository problemRepository;
    private final ObserviumMetricRepository metricRepository;
    private final ObserviumMetricMapper metricMapper;

    @Override
    @Transactional
    public int saveProblems(List<ObserviumProblemDTO> problems) {
        if (problems == null || problems.isEmpty()) {
            return 0;
        }

        List<ObserviumProblem> entitiesToSave = new ArrayList<>();
        Set<String> liveProblemIds = new HashSet<>();

        for (ObserviumProblemDTO dto : problems) {
            if (dto.getProblemId() == null || dto.getProblemId().isBlank()) {
                log.warn(EMPTY_PROBLEM_ID_LOG_TEMPLATE, dto);
                continue;
            }

            liveProblemIds.add(dto.getProblemId());
            ObserviumProblem incoming = toProblemEntity(dto);
            List<ObserviumProblem> existingList = problemRepository.findAllByProblemId(dto.getProblemId()).stream()
                    .sorted(Comparator.comparing(ObserviumProblem::getId))
                    .toList();

            if (existingList.isEmpty()) {
                entitiesToSave.add(incoming);
                continue;
            }

            ObserviumProblem existing = existingList.get(existingList.size() - 1);
            existing.setProblemId(incoming.getProblemId());
            existing.setHostId(incoming.getHostId());
            existing.setDevice(incoming.getDevice());
            existing.setDescription(incoming.getDescription());
            existing.setSeverity(incoming.getSeverity());
            existing.setActive(incoming.getActive());
            existing.setSource(incoming.getSource());
            existing.setEventId(incoming.getEventId());
            existing.setStartedAt(incoming.getStartedAt());
            existing.setResolvedAt(incoming.getResolvedAt());
            entitiesToSave.add(existing);

            if (existingList.size() > 1) {
                log.warn(DUPLICATE_PROBLEM_LOG_TEMPLATE, dto.getProblemId(), existingList.size());
            }
        }

        long resolvedAt = Instant.now().getEpochSecond();
        for (ObserviumProblem persistedActive : problemRepository.findByActiveTrue()) {
            if (persistedActive.getProblemId() == null || liveProblemIds.contains(persistedActive.getProblemId())) {
                continue;
            }

            persistedActive.setActive(false);
            if (persistedActive.getResolvedAt() == null || persistedActive.getResolvedAt() == 0L) {
                persistedActive.setResolvedAt(resolvedAt);
            }
            entitiesToSave.add(persistedActive);
        }

        return problemRepository.saveAll(entitiesToSave).size();
    }

    @Override
    @Transactional
    public int saveMetrics(List<ObserviumMetricDTO> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return 0;
        }

        List<ObserviumMetric> entitiesToSave = new ArrayList<>();

        for (ObserviumMetricDTO dto : metrics) {
            if (dto.getHostId() == null || dto.getHostId().isBlank()
                    || dto.getItemId() == null || dto.getItemId().isBlank()
                    || dto.getTimestamp() == null) {
                log.warn(EMPTY_METRIC_LOG_TEMPLATE, dto);
                continue;
            }

            ObserviumMetric incoming = metricMapper.toEntity(dto);
            ObserviumMetric entity = metricRepository.findByHostIdAndItemIdAndTimestamp(
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

    private ObserviumProblem toProblemEntity(ObserviumProblemDTO dto) {
        return ObserviumProblem.builder()
                .problemId(dto.getProblemId())
                .hostId(dto.getHostId())
                .device(dto.getHost())
                .description(dto.getDescription())
                .severity(dto.getSeverity())
                .active(dto.isActive())
                .source(MonitoringConstants.SOURCE_OBSERVIUM)
                .eventId(dto.getEventId())
                .startedAt(dto.getStartedAt())
                .resolvedAt(dto.getResolvedAt())
                .build();
    }
}
