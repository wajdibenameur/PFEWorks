package tn.iteam.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.domain.ZabbixProblem;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.mapper.ZabbixProblemMapper;
import tn.iteam.repository.ZabbixProblemRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ZabbixProblemService {

    private static final Logger log = LoggerFactory.getLogger(ZabbixProblemService.class);

    private final ZabbixAdapter zabbixAdapter;
    private final ZabbixProblemMapper mapper;
    private final ZabbixProblemRepository repository;

    public List<ZabbixProblemDTO> fetchActiveProblems() {
        log.info("Fetching active Zabbix problems");

        try {
            List<ZabbixProblemDTO> dtos = zabbixAdapter.fetchProblems();
            List<ZabbixProblem> entitiesToSave = new ArrayList<>();

            for (ZabbixProblemDTO dto : dtos) {
                if (dto.getProblemId() == null || dto.getProblemId().isBlank()) {
                    log.warn("Skipping problem with empty problemId: {}", dto);
                    continue;
                }

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

                    entitiesToSave.add(existing);

                    if (existingList.size() > 1) {
                        log.warn("Duplicate problemId={} found in DB: {} rows", entity.getProblemId(), existingList.size());
                    }
                } else {
                    entitiesToSave.add(entity);
                }
            }

            repository.saveAll(entitiesToSave);
            log.info("Saved {} Zabbix problems", entitiesToSave.size());

            return dtos;
        } catch (Exception e) {
            log.error("Error fetching Zabbix problems", e);
            return List.of();
        }
    }
}