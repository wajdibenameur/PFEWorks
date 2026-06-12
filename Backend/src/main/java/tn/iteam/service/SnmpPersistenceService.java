package tn.iteam.service;

import tn.iteam.dto.SnmpMetricDTO;
import tn.iteam.dto.SnmpProblemDTO;

import java.util.List;

public interface SnmpPersistenceService {

    int saveProblems(List<SnmpProblemDTO> problems);

    int saveMetrics(List<SnmpMetricDTO> metrics);
}
