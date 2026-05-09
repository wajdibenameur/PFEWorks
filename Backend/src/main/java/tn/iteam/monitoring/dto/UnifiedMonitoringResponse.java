package tn.iteam.monitoring.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Réponse unifiée pour les données de supervision")
public class UnifiedMonitoringResponse<T> {

    @Schema(description = "Données agrégées retournées par le service")
    private final T data;
    @Schema(description = "Indique si la réponse est dégradée à cause d'un fallback ou d'une source indisponible")
    private final boolean degraded;
    @Schema(description = "Indique la fraîcheur des données par source")
    private final Map<String, String> freshness;
    @Schema(description = "Informations de couverture complémentaires par source")
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
