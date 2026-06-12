package tn.iteam.service.snmp;

import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tn.iteam.adapter.snmp.SnmpDeviceSnapshot;
import tn.iteam.config.SnmpProperties;
import tn.iteam.domain.SnmpDevice;
import tn.iteam.enums.DeviceStatus;
import tn.iteam.exception.IntegrationResponseException;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;
import tn.iteam.repository.SnmpDeviceRepository;
import tn.iteam.service.SnmpCategoryMetricsService;
import tn.iteam.service.SnmpInterfaceCollectionService;
import tn.iteam.service.SnmpObservedStateService;
import tn.iteam.util.MonitoringConstants;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SnmpPollingServiceResilienceTest {

    @Mock
    private SnmpDeviceRepository deviceRepository;

    @Mock
    private SnmpObservedStateService observedStateService;
    @Mock
    private SnmpInterfaceCollectionService interfaceCollectionService;
    @Mock
    private SnmpCategoryMetricsService categoryMetricsService;

    @Mock
    private SnmpSubnetClassifier subnetClassifier;

    private ThreadPoolTaskExecutor executor;
    private SnmpPollingService service;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.initialize();

        SnmpProperties properties = new SnmpProperties();
        properties.setDefaultPort(161);
        properties.setDefaultCommunity("public");
        properties.setTimeoutMs(2000);
        properties.setRetries(1);
        properties.setMaxWorkers(20);

        RetryRegistry retryRegistry = RetryRegistry.of(
                RetryConfig.custom()
                        .maxAttempts(2)
                        .waitDuration(Duration.ofMillis(1))
                        .retryExceptions(IntegrationTimeoutException.class, IntegrationUnavailableException.class)
                        .ignoreExceptions(IntegrationResponseException.class)
                        .build()
        );

        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.of(
                TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(3))
                        .cancelRunningFuture(true)
                        .build()
        );

        service = spy(new SnmpPollingService(
                deviceRepository,
                observedStateService,
                interfaceCollectionService,
                categoryMetricsService,
                properties,
                subnetClassifier,
                executor,
                timeLimiterRegistry,
                retryRegistry
        ));

        when(subnetClassifier.resolveConfiguredCategory(any())).thenReturn(MonitoringConstants.CATEGORY_SWITCH);
        when(deviceRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(observedStateService.loadPreviousInterfacesByIndex(any())).thenReturn(Map.of());
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void timeoutException_retriesOnce_thenMarksDown_andCycleContinues() throws Exception {
        SnmpDevice first = device("10.0.0.1");
        SnmpDevice second = device("10.0.0.2");
        when(deviceRepository.findByEnabledTrueOrderByIpAddressAsc()).thenReturn(List.of(first, second));

        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new IntegrationTimeoutException(MonitoringConstants.SOURCE_SNMP, "timeout", null);
        }).when(service).pollDeviceWithTimeout(eq(first));
        doReturn(upSnapshot(second.getIpAddress())).when(service).pollDeviceWithTimeout(eq(second));

        List<SnmpDeviceSnapshot> snapshots = service.pollEnabledDevices();

        assertThat(attempts.get()).isEqualTo(2);
        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(0).getDeviceStatus()).isEqualTo(DeviceStatus.DOWN);
        assertThat(snapshots.get(1).getDeviceStatus()).isEqualTo(DeviceStatus.UP);
        verify(deviceRepository).findByEnabledTrueOrderByIpAddressAsc();
    }

    @Test
    void unavailableException_retriesOnce_thenMarksDown_andCycleContinues() throws Exception {
        SnmpDevice first = device("10.0.0.3");
        SnmpDevice second = device("10.0.0.4");
        when(deviceRepository.findByEnabledTrueOrderByIpAddressAsc()).thenReturn(List.of(first, second));

        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new IntegrationUnavailableException(MonitoringConstants.SOURCE_SNMP, "no response");
        }).when(service).pollDeviceWithTimeout(eq(first));
        doReturn(upSnapshot(second.getIpAddress())).when(service).pollDeviceWithTimeout(eq(second));

        List<SnmpDeviceSnapshot> snapshots = service.pollEnabledDevices();

        assertThat(attempts.get()).isEqualTo(2);
        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(0).getDeviceStatus()).isEqualTo(DeviceStatus.DOWN);
        assertThat(snapshots.get(1).getDeviceStatus()).isEqualTo(DeviceStatus.UP);
    }

    @Test
    void invalidConfiguration_doesNotRetry_marksDown_andCycleContinues() throws Exception {
        SnmpDevice first = device("10.0.0.5");
        SnmpDevice second = device("10.0.0.6");
        when(deviceRepository.findByEnabledTrueOrderByIpAddressAsc()).thenReturn(List.of(first, second));

        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new IntegrationResponseException(MonitoringConstants.SOURCE_SNMP, "community missing");
        }).when(service).pollDeviceWithTimeout(eq(first));
        doReturn(upSnapshot(second.getIpAddress())).when(service).pollDeviceWithTimeout(eq(second));

        List<SnmpDeviceSnapshot> snapshots = service.pollEnabledDevices();

        assertThat(attempts.get()).isEqualTo(1);
        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(0).getDeviceStatus()).isEqualTo(DeviceStatus.DOWN);
        assertThat(snapshots.get(0).getSysDescr()).isEqualTo(MonitoringConstants.UNKNOWN);
        assertThat(snapshots.get(1).getDeviceStatus()).isEqualTo(DeviceStatus.UP);
    }

    private SnmpDevice device(String ip) {
        SnmpDevice device = new SnmpDevice();
        device.setIpAddress(ip);
        device.setHostname("host-" + ip);
        device.setSnmpPort(161);
        device.setSnmpCommunity("public");
        device.setEnabled(true);
        device.setStatus(DeviceStatus.UNKNOWN);
        return device;
    }

    private SnmpDeviceSnapshot upSnapshot(String ip) {
        return SnmpDeviceSnapshot.builder()
                .ipAddress(ip)
                .hostId(ip)
                .hostName("host-" + ip)
                .category(MonitoringConstants.CATEGORY_SWITCH)
                .snmpPort(161)
                .status(MonitoringConstants.STATUS_UP)
                .deviceStatus(DeviceStatus.UP)
                .availability(1.0)
                .cpuPercent(null)
                .memoryPercent(null)
                .uptimeSeconds(100L)
                .sysDescr("ok")
                .interfaces(List.of())
                .extraMetrics(Map.of())
                .collectedAtEpochSec(System.currentTimeMillis() / 1000)
                .build();
    }
}
