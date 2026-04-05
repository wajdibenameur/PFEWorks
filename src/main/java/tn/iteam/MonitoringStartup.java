package tn.iteam;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.iteam.service.MonitoringService;
import tn.iteam.service.ZabbixProblemService;

@Component
@RequiredArgsConstructor
public class MonitoringStartup {

    private final MonitoringService monitoringService;
    private final ZabbixProblemService zabbixProblemService;

    @PostConstruct
    public void init() {
        // Chaque service est collecté automatiquement, séparément
        monitoringService.collectZabbix();
        monitoringService.collectObservium();
        monitoringService.collectZkBio();
        monitoringService.collectCamera();

    }
}
