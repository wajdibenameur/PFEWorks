package tn.iteam.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.domain.ZabbixProblem;
import tn.iteam.service.ZabbixProblemService;

import java.util.List;

@RestController
@RequestMapping("/api/zabbix")
@RequiredArgsConstructor
public class ZabbixProblemController {

    private final ZabbixProblemService service;

    @PostMapping("/collect")
    public ResponseEntity<String> collectProblems() {
        service.collectProblems();
        return ResponseEntity.ok("ZABBIX PROBLEMS COLLECTED");
    }

    @GetMapping("/active")
    public List<ZabbixProblem> allActive() {
        return service.allActiveProblems();
    }
}
