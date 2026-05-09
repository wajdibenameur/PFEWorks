package tn.iteam.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.service.MonitoredHostPersistenceService;

import java.util.List;

@Service
@ConditionalOnProperty(
        name = "app.db.enabled",
        havingValue = "false"
)
public class InMemoryMonitoredHostPersistenceService implements MonitoredHostPersistenceService {

    @Override
    public int saveAll(String source, List<ServiceStatusDTO> statuses) {
        if (statuses == null) {
            return 0;
        }
        return statuses.size();
    }
}