package tn.iteam.adapter.zabbix;

import tn.iteam.dto.ZabbixMetricDTO;

import java.util.List;

public record ZabbixMetricsCollectionResult(
        List<ZabbixMetricDTO> metrics,
        boolean partial
) {
}
