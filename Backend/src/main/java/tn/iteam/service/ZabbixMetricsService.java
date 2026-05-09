package tn.iteam.service;

import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;
import tn.iteam.adapter.zabbix.ZabbixMetricsCollectionResult;
import tn.iteam.domain.ZabbixMetric;

import java.util.List;

public interface ZabbixMetricsService {

    List<ZabbixMetric> getPersistedMetricsSnapshot();

    Mono<ZabbixMetricsRefreshResult> persistCollectedMetrics(ZabbixMetricsCollectionResult result);

    Mono<ZabbixMetricsRefreshResult> fetchMetricsRefreshResult();

    Mono<ZabbixMetricsRefreshResult> fetchMetricsRefreshResult(JsonNode hosts);

    Mono<List<ZabbixMetric>> fetchAndSaveMetrics();

    Mono<List<ZabbixMetric>> fetchAndSaveMetrics(JsonNode hosts);
}
