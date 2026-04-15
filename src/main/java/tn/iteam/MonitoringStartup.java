package tn.iteam;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.iteam.service.MonitoringService;

@Component
@RequiredArgsConstructor
public class MonitoringStartup {

    private final MonitoringService monitoringService;

    @PostConstruct
    public void init() {
        monitoringService.collectObservium();
        monitoringService.collectZkBio();
        monitoringService.collectCamera();
    }
}
