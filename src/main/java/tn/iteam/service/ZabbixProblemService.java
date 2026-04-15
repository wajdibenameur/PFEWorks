package tn.iteam.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.domain.ZabbixProblem;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.exception.IntegrationException;
import tn.iteam.mapper.ZabbixProblemMapper;
import tn.iteam.repository.ZabbixProblemRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ZabbixProblemService {

    private static final Logger log = LoggerFactory.getLogger(ZabbixProblemService.class);
    private static final List<String> EXPOSED_SEVERITIES = List.of("4", "5");

    private final ZabbixAdapter zabbixAdapter;
    private final ZabbixProblemMapper mapper;
    private final ZabbixProblemRepository repository;
    private final SourceAvailabilityService availabilityService;

    public List<ZabbixProblemDTO> getPersistedFilteredActiveProblems() {
        return repository.findByActiveTrueAndSeverityIn(EXPOSED_SEVERITIES).stream()
                .map(mapper::toDTO)
                .toList();
    }

    public List<ZabbixProblemDTO> synchronizeAndGetPersistedFilteredActiveProblems() {
        synchronizeActiveProblemsFromZabbix();
        return getPersistedFilteredActiveProblems();
    }

    @Transactional
    public List<ZabbixProblemDTO> synchronizeActiveProblemsFromZabbix() {
        return synchronizeActiveProblemsFromZabbix(null);
    }

    @Transactional
    public List<ZabbixProblemDTO> synchronizeActiveProblemsFromZabbix(JsonNode hosts) {
        log.info("Fetching active Zabbix problems");

        try {
            List<ZabbixProblemDTO> dtos = hosts == null
                    ? zabbixAdapter.fetchProblems()
                    : zabbixAdapter.fetchProblems(hosts);
            List<ZabbixProblem> entitiesToSave = new ArrayList<>();
            Set<String> liveProblemIds = new HashSet<>();

            for (ZabbixProblemDTO dto : dtos) {
                if (dto.getProblemId() == null || dto.getProblemId().isBlank()) {
                    log.warn("Skipping problem with empty problemId: {}", dto);
                    continue;
                }

                liveProblemIds.add(dto.getProblemId());

                ZabbixProblem entity = mapper.toEntity(dto);
                List<ZabbixProblem> existingList = repository.findAllByProblemId(entity.getProblemId());

                if (!existingList.isEmpty()) {
                    ZabbixProblem existing = existingList.get(0);

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

                    entitiesToSave.add(existing);

                    if (existingList.size() > 1) {
                        log.warn("Duplicate problemId={} found in DB: {} rows", entity.getProblemId(), existingList.size());
                    }
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
                persistedActive.setStatus("RESOLVED");
                entitiesToSave.add(persistedActive);
            }

            repository.saveAll(entitiesToSave);
            availabilityService.markAvailable("ZABBIX");
            log.info("Saved {} Zabbix problems, active in live feed={}", entitiesToSave.size(), liveProblemIds.size());

            return dtos;
        } catch (IntegrationException e) {
            availabilityService.markUnavailable("ZABBIX", e.getMessage());
            log.warn("Zabbix problems synchronization failed: {}", e.getMessage());
            return List.of();
        } catch (Exception e) {
            availabilityService.markUnavailable("ZABBIX", e.getMessage());
            log.error("Unexpected error fetching Zabbix problems", e);
            return List.of();
        }
    }
}
