package tn.iteam.integration;

import org.springframework.stereotype.Component;
import tn.iteam.monitoring.MonitoringSourceType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class IntegrationServiceRegistry {

    private final Map<MonitoringSourceType, AsyncIntegrationService> servicesBySource;

    public IntegrationServiceRegistry(List<AsyncIntegrationService> services) {
        Map<MonitoringSourceType, AsyncIntegrationService> map = new EnumMap<>(MonitoringSourceType.class);
        for (AsyncIntegrationService service : services) {
            map.put(service.getSourceType(), service);
        }
        this.servicesBySource = Map.copyOf(map);
    }

    public AsyncIntegrationService getRequired(MonitoringSourceType sourceType) {
        AsyncIntegrationService service = servicesBySource.get(sourceType);
        if (service == null) {
            throw new IllegalArgumentException("No integration service registered for source " + sourceType);
        }
        return service;
    }
}
