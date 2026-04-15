package tn.iteam.listener;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;
import tn.iteam.service.ZabbixLiveSynchronizationService;

@Component
@RequiredArgsConstructor
public class ZabbixStartupListener {

    private static final Logger log = LoggerFactory.getLogger(ZabbixStartupListener.class);

    private final ZabbixLiveSynchronizationService zabbixLiveSynchronizationService;
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Starting shared Zabbix warmup at application startup");

        try {
            zabbixLiveSynchronizationService.synchronizeForStartup();
        } catch (IntegrationTimeoutException | IntegrationUnavailableException e) {
            log.warn("Zabbix unavailable at startup: {}", e.getMessage());

            // IMPORTANT: NE PAS CRASHER
        } catch (Exception e) {
            log.error("Unexpected error during Zabbix startup sync", e);
        }
    }
}
