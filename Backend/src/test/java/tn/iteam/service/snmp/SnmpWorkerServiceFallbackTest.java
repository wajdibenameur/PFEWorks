package tn.iteam.service.snmp;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import tn.iteam.adapter.snmp.SnmpDeviceSnapshot;
import tn.iteam.config.SnmpProperties;
import tn.iteam.domain.SnmpDevice;
import tn.iteam.enums.DeviceStatus;
import tn.iteam.service.SnmpCategoryMetricsService;
import tn.iteam.service.SnmpInterfaceCollectionService;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SnmpWorkerServiceFallbackTest {

    private SnmpWorkerService service;

    @BeforeEach
    void setUp() {
        SnmpProperties properties = new SnmpProperties();
        properties.setDefaultPort(161);
        properties.setDefaultCommunity("public");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.initialize();

        SnmpSubnetClassifier subnetClassifier = mock(SnmpSubnetClassifier.class);
        when(subnetClassifier.resolveConfiguredCategory("192.168.130.52")).thenReturn("SWITCH");

        service = new SnmpWorkerService(
                properties,
                subnetClassifier,
                mock(SnmpInterfaceCollectionService.class),
                mock(SnmpCategoryMetricsService.class),
                executor
        );
    }

    @Test
    void callNotPermittedFallback_preservesPreviousState_andDoesNotMarkDown() throws Exception {
        SnmpDevice device = new SnmpDevice();
        device.setIpAddress("192.168.130.52");
        device.setHostname("hp-switch");
        device.setSnmpPort(161);
        device.setSnmpCommunity("public");
        device.setStatus(DeviceStatus.UP);
        device.setEnabled(true);

        @SuppressWarnings("unchecked")
        CompletableFuture<SnmpDeviceSnapshot> future = (CompletableFuture<SnmpDeviceSnapshot>) ReflectionTestUtils.invokeMethod(
                service,
                "pollDeviceFallback",
                device,
                CallNotPermittedException.createCallNotPermittedException(
                        io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("snmpApi")
                )
        );

        SnmpDeviceSnapshot snapshot = future.get();

        assertThat(snapshot.getDeviceStatus()).isEqualTo(DeviceStatus.UP);
        assertThat(snapshot.getStatus()).isEqualTo("UP");
        assertThat(snapshot.isPollAttempted()).isFalse();
        assertThat(snapshot.getSysDescr()).isEqualTo("SNMP_UNAVAILABLE");
        assertThat(snapshot.getDiagnosticReason()).contains("resilience rejected the call");
    }
}
