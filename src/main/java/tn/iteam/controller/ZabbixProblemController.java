package tn.iteam.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.service.ZabbixProblemService;

import java.util.List;

@RestController
@RequestMapping("/api/zabbix")
@RequiredArgsConstructor
public class ZabbixProblemController {

    private final ZabbixProblemService service;

    @GetMapping("/active")
    public List<ZabbixProblemDTO> allActive() {
        return service.getPersistedFilteredActiveProblems();
    }
}
