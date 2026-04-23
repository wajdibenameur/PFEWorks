package tn.iteam.service;

import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;
import tn.iteam.domain.ZabbixMetric;

import java.util.List;

public interface ZabbixMetricsService {

    List<ZabbixMetric> getPersistedMetricsSnapshot();

    Mono<List<ZabbixMetric>> synchronizeAndGetPersistedMetricsSnapshot();

    Mono<List<ZabbixMetric>> fetchAndSaveMetrics();

    Mono<List<ZabbixMetric>> fetchAndSaveMetrics(JsonNode hosts);
}
