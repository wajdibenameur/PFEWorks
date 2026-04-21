package tn.iteam.monitoring;

public enum MonitoringSourceType {
    ZABBIX(true, true, true, "native"),
    OBSERVIUM(true, true, true, "synthetic"),
    ZKBIO(true, true, true, "synthetic"),
    CAMERA(true, false, false, "not_applicable");

    private final boolean supportsHosts;
    private final boolean supportsProblems;
    private final boolean supportsMetrics;
    private final String metricsCoverage;

    MonitoringSourceType(
            boolean supportsHosts,
            boolean supportsProblems,
            boolean supportsMetrics,
            String metricsCoverage
    ) {
        this.supportsHosts = supportsHosts;
        this.supportsProblems = supportsProblems;
        this.supportsMetrics = supportsMetrics;
        this.metricsCoverage = metricsCoverage;
    }

    public boolean supportsDataset(String dataset) {
        return switch (dataset) {
            case "hosts" -> supportsHosts;
            case "problems" -> supportsProblems;
            case "metrics" -> supportsMetrics;
            default -> false;
        };
    }

    public String metricsCoverage() {
        return metricsCoverage;
    }
}
