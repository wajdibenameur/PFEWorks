package tn.iteam.events;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tn.iteam.websocket.MonitoringWebSocketPublisher;

@Component
public class MonitoringEventListener {

    private final MonitoringWebSocketPublisher monitoringWebSocketPublisher;

    public MonitoringEventListener(MonitoringWebSocketPublisher monitoringWebSocketPublisher) {
        this.monitoringWebSocketPublisher = monitoringWebSocketPublisher;
    }

    @Async
    @EventListener
    public void onSourceAvailabilityChanged(SourceAvailabilityChangedEvent event) {
        if (event == null || event.payload() == null) {
            return;
        }
        monitoringWebSocketPublisher.publishSourceAvailability(event.payload());
    }
}
