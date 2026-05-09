package tn.iteam.service;

import com.fasterxml.jackson.databind.JsonNode;
import tn.iteam.domain.MonitoredHost;

import java.util.Map;

public interface ZabbixHostSyncService {

    Map<String, MonitoredHost> loadHostMap();

    Map<String, MonitoredHost> loadHostMap(JsonNode hosts);
}