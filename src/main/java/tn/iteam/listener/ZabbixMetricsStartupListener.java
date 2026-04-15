package tn.iteam.listener;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tn.iteam.service.ZabbixMetricsService;

@Component
@RequiredArgsConstructor
public class ZabbixMetricsStartupListener {

    private static final Logger log = LoggerFactory.getLogger(ZabbixMetricsStartupListener.class);

    private final ZabbixMetricsService service;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("Triggering Zabbix metrics collection at startup");
        service.fetchAndSaveMetrics();
    }
}