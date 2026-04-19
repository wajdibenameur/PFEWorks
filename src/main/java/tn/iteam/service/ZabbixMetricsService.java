package tn.iteam.service;

import com.fasterxml.jackson.databind.JsonNode;
import tn.iteam.domain.ZabbixMetric;

import java.util.List;

public interface ZabbixMetricsService {

    List<ZabbixMetric> getPersistedMetricsSnapshot();

    List<ZabbixMetric> synchronizeAndGetPersistedMetricsSnapshot();

    List<ZabbixMetric> fetchAndSaveMetrics();

    List<ZabbixMetric> fetchAndSaveMetrics(JsonNode hosts);
}
