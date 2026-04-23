package tn.iteam.service.support;

import org.springframework.stereotype.Service;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.websocket.MonitoringWebSocketPublisher;
import tn.iteam.websocket.ZkBioWebSocketPublisher;

@Service
public class MonitoringSnapshotPublicationService {

    private final MonitoringWebSocketPublisher monitoringWebSocketPublisher;
    private final ZkBioWebSocketPublisher zkBioWebSocketPublisher;

    public MonitoringSnapshotPublicationService(
            MonitoringWebSocketPublisher monitoringWebSocketPublisher,
            ZkBioWebSocketPublisher zkBioWebSocketPublisher
    ) {
        this.monitoringWebSocketPublisher = monitoringWebSocketPublisher;
        this.zkBioWebSocketPublisher = zkBioWebSocketPublisher;
    }

    public void publishMonitoringSnapshots(MonitoringSourceType sourceType) {
        publishProblemsSnapshot(sourceType);
        publishMetricsSnapshot(sourceType);
    }

    public void publishProblemsSnapshot(MonitoringSourceType sourceType) {
        monitoringWebSocketPublisher.publishProblemsFromSnapshot(sourceType);
    }

    public void publishMetricsSnapshot(MonitoringSourceType sourceType) {
        monitoringWebSocketPublisher.publishMetricsFromSnapshot(sourceType);
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
}
