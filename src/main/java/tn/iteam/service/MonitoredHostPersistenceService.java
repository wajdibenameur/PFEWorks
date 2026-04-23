package tn.iteam.service;

import tn.iteam.dto.ServiceStatusDTO;

import java.util.List;

public interface MonitoredHostPersistenceService {

    int saveAll(String source, List<ServiceStatusDTO> statuses);
}
