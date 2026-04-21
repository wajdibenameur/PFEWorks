package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.domain.ZkBioMetric;
import tn.iteam.domain.ZkBioProblem;
import tn.iteam.dto.ZkBioMetricDTO;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.mapper.ZkBioMetricMapper;
import tn.iteam.repository.ZkBioMetricRepository;
import tn.iteam.repository.ZkBioProblemRepository;
import tn.iteam.service.ZkBioPersistenceService;
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
public class ZkBioPersistenceServiceImpl implements ZkBioPersistenceService {

    private static final String EMPTY_PROBLEM_ID_LOG_TEMPLATE = "Skipping ZKBio problem without problemId: {}";
    private static final String EMPTY_METRIC_LOG_TEMPLATE = "Skipping ZKBio metric with empty hostId/itemId/timestamp: {}";
    private static final String DUPLICATE_PROBLEM_LOG_TEMPLATE = "Duplicate ZKBio problemId={} found in DB: {} rows";

    private final ZkBioProblemRepository problemRepository;
    private final ZkBioMetricRepository metricRepository;
    private final ZkBioMetricMapper metricMapper;

    @Override
    @Transactional
    public int saveProblems(List<ZkBioProblemDTO> problems) {
        if (problems == null || problems.isEmpty()) {
            return 0;
        }

        List<ZkBioProblem> entitiesToSave = new ArrayList<>();
        Set<String> liveProblemIds = new HashSet<>();

        for (ZkBioProblemDTO dto : problems) {
            if (dto.getProblemId() == null || dto.getProblemId().isBlank()) {
                log.warn(EMPTY_PROBLEM_ID_LOG_TEMPLATE, dto);
                continue;
            }

            liveProblemIds.add(dto.getProblemId());
            ZkBioProblem incoming = toProblemEntity(dto);
            List<ZkBioProblem> existingList = problemRepository.findAllByProblemId(dto.getProblemId()).stream()
                    .sorted(Comparator.comparing(ZkBioProblem::getId))
                    .toList();

            if (existingList.isEmpty()) {
                entitiesToSave.add(incoming);
                continue;
            }

            ZkBioProblem existing = existingList.get(existingList.size() - 1);
            existing.setProblemId(incoming.getProblemId());
            existing.setDevice(incoming.getDevice());
            existing.setDescription(incoming.getDescription());
            existing.setActive(incoming.getActive());
            existing.setStatus(incoming.getStatus());
            existing.setStartedAt(incoming.getStartedAt());
            existing.setResolvedAt(incoming.getResolvedAt());
            existing.setSource(incoming.getSource());
            existing.setEventId(incoming.getEventId());
            entitiesToSave.add(existing);

            if (existingList.size() > 1) {
                log.warn(DUPLICATE_PROBLEM_LOG_TEMPLATE, dto.getProblemId(), existingList.size());
            }
        }

        long resolvedAt = Instant.now().getEpochSecond();
        for (ZkBioProblem persistedActive : problemRepository.findByActiveTrue()) {
            if (persistedActive.getProblemId() == null || liveProblemIds.contains(persistedActive.getProblemId())) {
                continue;
            }

            persistedActive.setActive(false);
            persistedActive.setStatus(MonitoringConstants.STATUS_RESOLVED);
            if (persistedActive.getResolvedAt() == null || persistedActive.getResolvedAt() == 0L) {
                persistedActive.setResolvedAt(resolvedAt);
            }
            entitiesToSave.add(persistedActive);
        }

        return problemRepository.saveAll(entitiesToSave).size();
    }

    @Override
    @Transactional
    public int saveMetrics(List<ZkBioMetricDTO> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return 0;
        }

        List<ZkBioMetric> entitiesToSave = new ArrayList<>();

        for (ZkBioMetricDTO dto : metrics) {
            if (dto.getHostId() == null || dto.getHostId().isBlank()
                    || dto.getItemId() == null || dto.getItemId().isBlank()
                    || dto.getTimestamp() == null) {
                log.warn(EMPTY_METRIC_LOG_TEMPLATE, dto);
                continue;
            }

            ZkBioMetric incoming = metricMapper.toEntity(dto);
            ZkBioMetric entity = metricRepository.findByHostIdAndItemIdAndTimestamp(
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

    private ZkBioProblem toProblemEntity(ZkBioProblemDTO dto) {
        return ZkBioProblem.builder()
                .problemId(dto.getProblemId())
                .device(dto.getHost())
                .description(dto.getDescription())
                .active(dto.isActive())
                .status(dto.getStatus())
                .startedAt(dto.getStartedAt())
                .resolvedAt(dto.getResolvedAt())
                .source(MonitoringConstants.SOURCE_ZKBIO)
                .eventId(dto.getEventId())
                .build();
    }
}
