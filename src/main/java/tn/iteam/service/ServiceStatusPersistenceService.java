package tn.iteam.service;

import tn.iteam.dto.ServiceStatusDTO;

import java.util.List;

public interface ServiceStatusPersistenceService {

    int saveAll(List<ServiceStatusDTO> statuses);
}
