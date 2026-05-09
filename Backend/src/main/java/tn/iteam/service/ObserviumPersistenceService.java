package tn.iteam.service;

import tn.iteam.dto.ObserviumMetricDTO;
import tn.iteam.dto.ObserviumProblemDTO;

import java.util.List;

public interface ObserviumPersistenceService {

    int saveProblems(List<ObserviumProblemDTO> problems);

    int saveMetrics(List<ObserviumMetricDTO> metrics);
}
