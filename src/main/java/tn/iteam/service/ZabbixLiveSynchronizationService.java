package tn.iteam.service;

public interface ZabbixLiveSynchronizationService {

    void synchronizeForStartup();

    void synchronizeForProblemsTick();

    void synchronizeForMetricsTick();
}
