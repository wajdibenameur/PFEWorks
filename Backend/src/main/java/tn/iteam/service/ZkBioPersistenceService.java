package tn.iteam.service;

import tn.iteam.dto.ZkBioMetricDTO;
import tn.iteam.dto.ZkBioProblemDTO;

import java.util.List;

public interface ZkBioPersistenceService {

    int saveProblems(List<ZkBioProblemDTO> problems);

    int saveMetrics(List<ZkBioMetricDTO> metrics);
}
