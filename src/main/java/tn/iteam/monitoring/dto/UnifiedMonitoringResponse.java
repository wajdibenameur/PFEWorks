package tn.iteam.monitoring.dto;

import java.util.Map;

public class UnifiedMonitoringResponse<T> {

    private final T data;
    private final boolean degraded;
    private final Map<String, String> freshness;
    private final Map<String, String> coverage;

    public UnifiedMonitoringResponse(T data, boolean degraded, Map<String, String> freshness, Map<String, String> coverage) {
        this.data = data;
        this.degraded = degraded;
        this.freshness = freshness;
        this.coverage = coverage;
    }

    public T getData() {
        return data;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public Map<String, String> getFreshness() {
        return freshness;
    }

    public Map<String, String> getCoverage() {
        return coverage;
    }
}
