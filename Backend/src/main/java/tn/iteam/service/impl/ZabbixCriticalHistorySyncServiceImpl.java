package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.adapter.zabbix.ZabbixCriticalEventHistoryCollector;
import tn.iteam.domain.ZabbixOldProblem;
import tn.iteam.domain.ZabbixProblem;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.mapper.ZabbixProblemMapper;
import tn.iteam.repository.ZabbixOldProblemRepository;
import tn.iteam.repository.ZabbixProblemRepository;
import tn.iteam.service.ZabbixCriticalHistorySyncResult;
import tn.iteam.service.ZabbixCriticalHistorySyncService;
import tn.iteam.service.support.ZabbixProblemSanitizer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ZabbixCriticalHistorySyncServiceImpl implements ZabbixCriticalHistorySyncService {

    private static final Logger log = LoggerFactory.getLogger(ZabbixCriticalHistorySyncServiceImpl.class);

    private final ZabbixCriticalEventHistoryCollector collector;
    private final ZabbixProblemRepository problemRepository;
    private final ZabbixOldProblemRepository oldProblemRepository;
    private final ZabbixProblemMapper problemMapper;
    private final ZabbixProblemSanitizer problemSanitizer;

    @Value("${zabbix.critical-history.enabled:false}")
    private boolean enabled;

    @Override
    @Transactional
    public ZabbixCriticalHistorySyncResult syncCriticalHistory() {
        if (!enabled) {
            log.warn("Zabbix critical history sync skipped because zabbix.critical-history.enabled=false");
            return ZabbixCriticalHistorySyncResult.disabled();
        }

        List<ZabbixProblemDTO> collected = collector.collectCriticalHistory();
        if (collected.isEmpty()) {
            return ZabbixCriticalHistorySyncResult.emptyEnabled();
        }

        int duplicatesIgnored = 0;
        int invalidIgnored = 0;
        List<ZabbixProblem> entitiesToInsert = new ArrayList<>();
        List<ZabbixOldProblem> oldEntitiesToInsert = new ArrayList<>();
        Set<Long> seenEventIds = new HashSet<>();

        for (ZabbixProblemDTO dto : collected) {
            if (dto.getEventId() == null
                    || !seenEventIds.add(dto.getEventId())
                    || oldProblemRepository.existsByEventId(dto.getEventId())) {
                duplicatesIgnored++;
                continue;
            }

            ZabbixProblemDTO sanitized = problemSanitizer.sanitize(dto, log);
            if (sanitized == null || sanitized.getEventId() == null) {
                invalidIgnored++;
                continue;
            }

            if (!"4".equals(sanitized.getSeverity()) && !"5".equals(sanitized.getSeverity())) {
                invalidIgnored++;
                continue;
            }

            ZabbixProblem entity = problemMapper.toEntity(sanitized);
            entitiesToInsert.add(entity);
            oldEntitiesToInsert.add(toOldProblemEntity(entity));
        }

        if (!entitiesToInsert.isEmpty()) {
            log.info("Persisting {} rows into zabbix_problem and {} rows into zabbix_old_problem",
                    entitiesToInsert.size(), oldEntitiesToInsert.size());
            problemRepository.saveAll(entitiesToInsert);
            problemRepository.flush();
            oldProblemRepository.saveAll(oldEntitiesToInsert);
            oldProblemRepository.flush();
            log.info("Persisted critical history archive rows into zabbix_old_problem count={}", oldEntitiesToInsert.size());
        } else {
            log.warn("No rows prepared for zabbix_old_problem persistence. duplicatesIgnored={} invalidIgnored={} found={}",
                    duplicatesIgnored, invalidIgnored, collected.size());
        }

        return new ZabbixCriticalHistorySyncResult(
                true,
                collected.size(),
                entitiesToInsert.size(),
                duplicatesIgnored,
                invalidIgnored,
                collected.isEmpty() ? "0 critical events found" : "critical history sync completed"
        );
    }

    private ZabbixOldProblem toOldProblemEntity(ZabbixProblem problem) {
        return ZabbixOldProblem.builder()
                .problemId(problem.getProblemId())
                .hostId(problem.getHostId())
                .host(problem.getHost())
                .description(problem.getDescription())
                .severity(problem.getSeverity())
                .active(problem.getActive())
                .ip(problem.getIp())
                .port(problem.getPort())
                .source(problem.getSource())
                .eventId(problem.getEventId())
                .startedAt(problem.getStartedAt())
                .resolvedAt(problem.getResolvedAt())
                .status(problem.getStatus())
                .build();
    }
}
