package tn.iteam.service;

import com.fasterxml.jackson.databind.JsonNode;
import tn.iteam.dto.ZabbixProblemDTO;

import java.util.List;

public interface ZabbixProblemService {

    List<ZabbixProblemDTO> getPersistedFilteredActiveProblems();

    List<ZabbixProblemDTO> synchronizeAndGetPersistedFilteredActiveProblems();

    List<ZabbixProblemDTO> synchronizeActiveProblemsFromZabbix();

    List<ZabbixProblemDTO> synchronizeActiveProblemsFromZabbix(JsonNode hosts);
}
