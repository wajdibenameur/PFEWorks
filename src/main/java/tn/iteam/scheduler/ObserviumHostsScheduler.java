package tn.iteam.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.iteam.service.MonitoringService;

@Slf4j
@Component
@RequiredArgsConstructor
public class ObserviumHostsScheduler {

    private final MonitoringService monitoringService;

    @Scheduled(
            fixedRateString = "${observium.scheduler.hosts.rate:120000}",
            initialDelayString = "${observium.scheduler.hosts.initial-delay:60000}"
    )
    public void refreshHosts() {
        log.debug("Scheduled: refreshing Observium hosts");
        monitoringService.collectObserviumHosts();
    }
}
