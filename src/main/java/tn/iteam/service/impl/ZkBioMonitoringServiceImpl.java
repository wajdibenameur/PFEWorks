package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.zkbio.ZkBioAdapter;
import tn.iteam.mapper.ZkBioMonitoringMapper;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.service.ZkBioMonitoringService;

import java.util.List;

/**
 * Implementation of ZkBioMonitoringService.
 * This service is dedicated to the unified monitoring layer and provides
 * hosts and problems data for the monitoring aggregation.
 * It delegates to ZkBioAdapter which handles the Redis fallback logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZkBioMonitoringServiceImpl implements ZkBioMonitoringService {

    private final ZkBioAdapter zkBioAdapter;
    private final ZkBioMonitoringMapper monitoringMapper;

    @Override
    public List<UnifiedMonitoringHostDTO> fetchMonitoringHosts() {
        log.debug("Fetching monitoring hosts from ZKBio");
        return zkBioAdapter.fetchAll().stream()
                .map(monitoringMapper::toHost)
                .toList();
    }

    @Override
    public List<UnifiedMonitoringProblemDTO> fetchMonitoringProblems() {
        log.debug("Fetching monitoring problems from ZKBio");
        return zkBioAdapter.fetchProblems().stream()
                .map(monitoringMapper::toProblem)
                .toList();
    }
}