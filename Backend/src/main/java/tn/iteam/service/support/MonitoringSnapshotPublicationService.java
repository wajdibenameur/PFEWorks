package tn.iteam.service.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.snapshot.SnapshotStore;
import tn.iteam.websocket.MonitoringWebSocketPublisher;
import tn.iteam.websocket.ZkBioWebSocketPublisher;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MonitoringSnapshotPublicationService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringSnapshotPublicationService.class);
    private static final String DATASET_PROBLEMS = "problems";
    private static final String DATASET_METRICS = "metrics";
    private static final long DEFAULT_MIN_PUBLISH_INTERVAL_MS = 5000L;
    private static final long DEFAULT_PUBLISH_TRACKING_TTL_MS = 3600000L;

    private final MonitoringWebSocketPublisher monitoringWebSocketPublisher;
    private final ZkBioWebSocketPublisher zkBioWebSocketPublisher;
    private final SnapshotStore snapshotStore;
    private final MonitoringFreshnessService freshnessService;
    private final Map<String, Long> lastPublishByKey = new ConcurrentHashMap<>();

    @Value("${app.websocket.monitoring.min-publish-interval-ms:5000}")
    private long minPublishIntervalMs = DEFAULT_MIN_PUBLISH_INTERVAL_MS;

    @Value("${app.websocket.publish-only-on-change:true}")
    private boolean publishOnlyOnChange;

    @Value("${app.websocket.monitoring.publish-tracking-ttl-ms:3600000}")
    private long publishTrackingTtlMs = DEFAULT_PUBLISH_TRACKING_TTL_MS;

    @Value("${app.websocket.monitoring.max-items-per-payload:500}")
    private int maxItemsPerPayload;

    public MonitoringSnapshotPublicationService(
            MonitoringWebSocketPublisher monitoringWebSocketPublisher,
            ZkBioWebSocketPublisher zkBioWebSocketPublisher,
            SnapshotStore snapshotStore,
            MonitoringFreshnessService freshnessService
    ) {
        this.monitoringWebSocketPublisher = monitoringWebSocketPublisher;
        this.zkBioWebSocketPublisher = zkBioWebSocketPublisher;
        this.snapshotStore = snapshotStore;
        this.freshnessService = freshnessService;
    }

    public void publishMonitoringSnapshots(MonitoringSourceType sourceType) {
        publishProblemsSnapshot(sourceType);
        publishMetricsSnapshot(sourceType);
    }

    public void publishProblemsSnapshot(MonitoringSourceType sourceType) {
        if (sourceType == null) {
            return;
        }
        if (!shouldPublish(DATASET_PROBLEMS, sourceType)) {
            log.debug("Skipping monitoring problems websocket publish for {} due to publish throttle", sourceType);
            return;
        }

        snapshotStore.<List<UnifiedMonitoringProblemDTO>>get(DATASET_PROBLEMS, sourceType.name())
                .ifPresent(snapshot -> {
                    List<UnifiedMonitoringProblemDTO> problems = capPayloadSize(snapshot.data(), DATASET_PROBLEMS, sourceType);
                    if (publishOnlyOnChange && !freshnessService.hasPublishDelta(DATASET_PROBLEMS, sourceType.name(), problems)) {
                        log.debug("WS PUBLISH SKIPPED no delta dataset={} source={}", DATASET_PROBLEMS, sourceType.name());
                        return;
                    }
                    monitoringWebSocketPublisher.publishProblems(problems);
                    freshnessService.markPublished(DATASET_PROBLEMS, sourceType.name(), problems);
                });
    }

    public void publishMetricsSnapshot(MonitoringSourceType sourceType) {
        if (sourceType == null) {
            return;
        }
        if (!shouldPublish(DATASET_METRICS, sourceType)) {
            log.debug("Skipping monitoring metrics websocket publish for {} due to publish throttle", sourceType);
            return;
        }

        snapshotStore.<List<UnifiedMonitoringMetricDTO>>get(DATASET_METRICS, sourceType.name())
                .ifPresent(snapshot -> {
                    List<UnifiedMonitoringMetricDTO> metrics = capPayloadSize(snapshot.data(), DATASET_METRICS, sourceType);
                    if (publishOnlyOnChange && !freshnessService.hasPublishDelta(DATASET_METRICS, sourceType.name(), metrics)) {
                        log.debug("WS PUBLISH SKIPPED no delta dataset={} source={}", DATASET_METRICS, sourceType.name());
                        return;
                    }
                    monitoringWebSocketPublisher.publishMetrics(metrics);
                    freshnessService.markPublished(DATASET_METRICS, sourceType.name(), metrics);
                });
    }

    public void publishMonitoringSnapshots(Iterable<MonitoringSourceType> sourceTypes) {
        for (MonitoringSourceType sourceType : sourceTypes) {
            publishMonitoringSnapshots(sourceType);
        }
    }

    public void publishZkBioSnapshots() {
        zkBioWebSocketPublisher.publishAttendanceFromSnapshot();
        zkBioWebSocketPublisher.publishDevicesFromSnapshot();
        zkBioWebSocketPublisher.publishStatusFromSnapshot();
    }

    private boolean shouldPublish(String dataset, MonitoringSourceType sourceType) {
        cleanupPublishTrackingMap();
        long now = System.currentTimeMillis();
        String key = dataset + ":" + sourceType.name();
        long effectiveInterval = minPublishIntervalMs > 0 ? minPublishIntervalMs : DEFAULT_MIN_PUBLISH_INTERVAL_MS;
        Long previous = lastPublishByKey.putIfAbsent(key, now);
        if (previous == null) {
            return true;
        }
        if (now - previous < effectiveInterval) {
            return false;
        }
        lastPublishByKey.put(key, now);
        return true;
    }

    private <T> List<T> capPayloadSize(List<T> source, String dataset, MonitoringSourceType sourceType) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        int limit = maxItemsPerPayload > 0 ? maxItemsPerPayload : 500;
        if (source.size() <= limit) {
            return source;
        }
        log.debug("Truncating websocket payload dataset={} source={} size={} limit={}",
                dataset, sourceType, source.size(), limit);
        return source.subList(0, limit);
    }

    private void cleanupPublishTrackingMap() {
        long now = System.currentTimeMillis();
        long ttl = publishTrackingTtlMs > 0 ? publishTrackingTtlMs : DEFAULT_PUBLISH_TRACKING_TTL_MS;
        lastPublishByKey.entrySet().removeIf(entry -> now - entry.getValue() > ttl);
    }
}
