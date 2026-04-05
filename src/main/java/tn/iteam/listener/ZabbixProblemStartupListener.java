package tn.iteam.listener;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tn.iteam.service.ZabbixProblemService;

@Component
@RequiredArgsConstructor
public class ZabbixProblemStartupListener {

    private static final Logger log = LoggerFactory.getLogger(ZabbixProblemStartupListener.class);
    private final ZabbixProblemService zabbixProblemService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Déclenchement de la collecte des problèmes Zabbix au démarrage");
        zabbixProblemService.collectProblems();
    }
}