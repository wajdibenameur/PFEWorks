package tn.iteam.service;

import tn.iteam.domain.ZabbixMetric;

import java.util.List;

public record ZabbixMetricsRefreshResult(
        List<ZabbixMetric> metrics,
        boolean partial
) {
}
