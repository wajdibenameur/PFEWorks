package tn.iteam.service;

import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;

import java.util.List;

public interface MonitoredHostSnapshotService {

    List<UnifiedMonitoringHostDTO> loadHosts(MonitoringSourceType source);
}
