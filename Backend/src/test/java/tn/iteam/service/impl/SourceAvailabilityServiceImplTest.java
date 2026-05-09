package tn.iteam.service.impl;

import org.junit.jupiter.api.Test;
import tn.iteam.dto.SourceAvailabilityDTO;
import tn.iteam.websocket.MonitoringWebSocketPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SourceAvailabilityServiceImplTest {

    @Test
    void markDegradedExposesDegradedStateAndPublishesIt() {
        MonitoringWebSocketPublisher publisher = mock(MonitoringWebSocketPublisher.class);
        SourceAvailabilityServiceImpl service = new SourceAvailabilityServiceImpl(publisher);

        service.markDegraded("OBSERVIUM", "Redis snapshot in use");

        SourceAvailabilityDTO dto = service.get("OBSERVIUM");

        assertThat(service.isDegraded("OBSERVIUM")).isTrue();
        assertThat(service.isAvailable("OBSERVIUM")).isFalse();
        assertThat(service.getStatus("OBSERVIUM")).isEqualTo("DEGRADED");
        assertThat(dto.getStatus()).isEqualTo("DEGRADED");
        assertThat(dto.isAvailable()).isFalse();
        assertThat(dto.getLastError()).isEqualTo("Redis snapshot in use");
        verify(publisher).publishSourceAvailability(argThat(event ->
                "OBSERVIUM".equals(event.getSource())
                        && "DEGRADED".equals(event.getStatus())
                        && "Redis snapshot in use".equals(event.getLastError())
        ));
    }

    @Test
    void transitioningFromDegradedToAvailablePublishesBothStateChanges() {
        MonitoringWebSocketPublisher publisher = mock(MonitoringWebSocketPublisher.class);
        SourceAvailabilityServiceImpl service = new SourceAvailabilityServiceImpl(publisher);

        service.markDegraded("ZKBIO", "Fallback snapshot");
        service.markAvailable("ZKBIO");

        SourceAvailabilityDTO dto = service.get("ZKBIO");

        assertThat(service.isDegraded("ZKBIO")).isFalse();
        assertThat(service.isAvailable("ZKBIO")).isTrue();
        assertThat(dto.getStatus()).isEqualTo("AVAILABLE");
        assertThat(dto.isAvailable()).isTrue();
        assertThat(dto.getLastError()).isNull();
        verify(publisher, times(2)).publishSourceAvailability(argThat(event ->
                "ZKBIO".equals(event.getSource())
        ));
    }
}
