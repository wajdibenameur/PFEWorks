package tn.iteam.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.dto.ZabbixProblemDTO;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ZabbixProblemService {

    private static final Logger log = LoggerFactory.getLogger(ZabbixProblemService.class);

    private final ZabbixAdapter zabbixAdapter;

    public List<ZabbixProblemDTO> fetchActiveProblems() {
        log.info("Fetching active Zabbix problems");
        try {
            return zabbixAdapter.fetchProblems();
        } catch (Exception e) {
            log.error("Error fetching Zabbix problems", e);
            return List.of();
        }
    }
}