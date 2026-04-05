package tn.iteam.service;

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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


@Service
@RequiredArgsConstructor
public class ZabbixProblemService {

    private static final Logger log = LoggerFactory.getLogger(ZabbixProblemService.class);

    private final ZabbixAdapter zabbixAdapter;        // ← injection de l'adaptateur
    private final ZabbixProblemRepository repository;
    private final ZabbixProblemMapper mapper;         // ← pour convertir DTO → entité

    @Transactional
    public void collectProblems() {
        log.info("===== Collecte des problèmes via l'adaptateur Zabbix =====");

        try {
            // 1. Récupérer les DTO depuis l'adaptateur
            List<ZabbixProblemDTO> dtos = zabbixAdapter.fetchProblems();
            log.info("DTOs reçus : {}", dtos.size());

            // 2. Convertir en entités (via mapper)
            List<ZabbixProblem> problems = dtos.stream()
                    .map(mapper::toEntity)
                    .toList();

            // 3. Synchronisation en base avec AtomicInteger
            AtomicInteger inserted = new AtomicInteger(0);
            AtomicInteger updated = new AtomicInteger(0);

            for (ZabbixProblem p : problems) {
                repository.findByProblemId(p.getProblemId())
                        .ifPresentOrElse(existing -> {
                            existing.setActive(p.getActive());
                            existing.setHost(p.getHost());
                            existing.setDescription(p.getDescription());
                            existing.setSeverity(p.getSeverity());
                            repository.save(existing);
                            updated.incrementAndGet();
                        }, () -> {
                            repository.save(p);
                            inserted.incrementAndGet();
                        });
            }

            log.info("Synchronisation terminée : {} insérés, {} mis à jour",
                    inserted.get(), updated.get());

        } catch (Exception e) {
            log.error("Erreur lors de la collecte des problèmes", e);
        }
    }
    public List<ZabbixProblem> allActiveProblems() {
        return repository.findByActiveTrue();
    }
}