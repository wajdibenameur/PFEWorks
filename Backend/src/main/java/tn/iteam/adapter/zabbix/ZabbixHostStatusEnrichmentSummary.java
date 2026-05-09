package tn.iteam.adapter.zabbix;

public record ZabbixHostStatusEnrichmentSummary(
        int hostsEnriched,
        int downByPing,
        int degradedByProblems,
        int degradedByPartialMetrics
) {
}
