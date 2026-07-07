package tn.iteam.ml.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ml.risk")
public record MlRiskProperties(
        double watchProbabilityThreshold,
        double riskProbabilityThreshold,
        double cpuWatchThreshold,
        double cpuRiskThreshold,
        double ramWatchThreshold,
        double ramRiskThreshold,
        double latencyWatchThresholdMs,
        double latencyRiskThresholdMs,
        double packetLossWatchThreshold,
        double packetLossRiskThreshold,
        int highSeverityThreshold,
        int criticalSeverityThreshold,
        int disasterSeverityThreshold
) {
}
