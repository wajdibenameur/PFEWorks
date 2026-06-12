package tn.iteam.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import tn.iteam.dto.SourceAvailabilityDTO;
import tn.iteam.events.SourceAvailabilityChangedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SourceAvailabilityServiceImplTest {

    @Test
    void markDegradedExposesDegradedStateAndPublishesIt() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        SourceAvailabilityServiceImpl service = new SourceAvailabilityServiceImpl(publisher);

        service.markDegraded("SNMP", "Redis snapshot in use");

        SourceAvailabilityDTO dto = service.get("SNMP");

        assertThat(service.isDegraded("SNMP")).isTrue();
        assertThat(service.isAvailable("SNMP")).isFalse();
        assertThat(service.getStatus("SNMP")).isEqualTo("DEGRADED");
        assertThat(dto.getStatus()).isEqualTo("DEGRADED");
        assertThat(dto.isAvailable()).isFalse();
        assertThat(dto.getLastError()).isEqualTo("Redis snapshot in use");
        verify(publisher).publishEvent(argThat((SourceAvailabilityChangedEvent event) ->
                "SNMP".equals(event.payload().getSource())
                        && "DEGRADED".equals(event.payload().getStatus())
                        && "Redis snapshot in use".equals(event.payload().getLastError())
        ));
    }

    @Test
    void transitioningFromDegradedToAvailablePublishesBothStateChanges() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        SourceAvailabilityServiceImpl service = new SourceAvailabilityServiceImpl(publisher);

        service.markDegraded("ZKBIO", "Fallback snapshot");
        service.markAvailable("ZKBIO");

        SourceAvailabilityDTO dto = service.get("ZKBIO");

        assertThat(service.isDegraded("ZKBIO")).isFalse();
        assertThat(service.isAvailable("ZKBIO")).isTrue();
        assertThat(dto.getStatus()).isEqualTo("AVAILABLE");
        assertThat(dto.isAvailable()).isTrue();
        assertThat(dto.getLastError()).isNull();
        verify(publisher, times(2)).publishEvent(argThat((SourceAvailabilityChangedEvent event) ->
                "ZKBIO".equals(event.payload().getSource())
        ));
    }
}
