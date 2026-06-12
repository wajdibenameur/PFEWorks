package tn.iteam.service;

import tn.iteam.dto.SnmpMetricDTO;
import tn.iteam.dto.SnmpProblemDTO;
import tn.iteam.dto.ServiceStatusDTO;

import java.util.List;

public interface SnmpSourceService {

    List<ServiceStatusDTO> fetchAll();

    List<SnmpProblemDTO> fetchProblems();

    List<SnmpMetricDTO> fetchMetrics();
}
