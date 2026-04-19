package tn.iteam.service;

public interface MonitoringService {

    void collectAll();

    void collectZabbix();

    void collectObservium();

    void collectObserviumHosts();

    void collectZkBio();

    void collectCamera();
}
