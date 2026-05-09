package tn.iteam.service;

import tn.iteam.dto.DashboardAnomalyDTO;
import tn.iteam.dto.DashboardOverviewDTO;
import tn.iteam.dto.DashboardPredictionDTO;

import java.util.List;

public interface DashboardService {

    DashboardOverviewDTO getOverview();

    List<DashboardPredictionDTO> getPredictions();

    List<DashboardAnomalyDTO> getAnomalies();
}
