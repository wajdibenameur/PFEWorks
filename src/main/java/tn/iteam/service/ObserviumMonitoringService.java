package tn.iteam.service;

import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;

import java.util.List;

/**
 * Interface for Observium monitoring operations.
 * This service is dedicated to the unified monitoring layer and should only contain
 * methods that return data for the monitoring aggregation (hosts, problems, metrics).
 */
public interface ObserviumMonitoringService {

    /**
     * Fetches all hosts from Observium in unified monitoring format.
     * @return list of unified monitoring host DTOs
     */
    List<UnifiedMonitoringHostDTO> fetchMonitoringHosts();

    /**
     * Fetches all problems from Observium in unified monitoring format.
     * @return list of unified monitoring problem DTOs
     */
    List<UnifiedMonitoringProblemDTO> fetchMonitoringProblems();
}