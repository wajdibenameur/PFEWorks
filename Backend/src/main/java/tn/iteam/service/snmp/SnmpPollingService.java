package tn.iteam.service.snmp;

import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.snmp.SnmpDeviceSnapshot;
import tn.iteam.config.SnmpProperties;
import tn.iteam.domain.SnmpDevice;
import tn.iteam.enums.DeviceStatus;
import tn.iteam.exception.IntegrationResponseException;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;
import tn.iteam.repository.SnmpDeviceRepository;
import tn.iteam.service.SnmpMonitoringService;
import tn.iteam.service.SnmpObservedStateService;
import tn.iteam.util.MonitoringConstants;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
public class SnmpPollingService implements SnmpMonitoringService {

    private static final String SOURCE = "SNMP";
    private static final String RESILIENCE_NAME = "snmpApi";

    private final SnmpDeviceRepository deviceRepository;
    private final SnmpObservedStateService observedStateService;
    private final SnmpBatchDispatcher snmpBatchDispatcher;
    private final SnmpWorkerService snmpWorkerService;

    private final SnmpProperties legacyProperties;
    private final ThreadPoolTaskExecutor legacyExecutor;
    private final TimeLimiterRegistry legacyTimeLimiterRegistry;
    private final RetryRegistry legacyRetryRegistry;
    private final boolean legacyMode;

    @Autowired
    public SnmpPollingService(
            SnmpDeviceRepository deviceRepository,
            SnmpObservedStateService observedStateService,
            SnmpBatchDispatcher snmpBatchDispatcher,
            SnmpWorkerService snmpWorkerService
    ) {
        this.deviceRepository = deviceRepository;
        this.observedStateService = observedStateService;
        this.snmpBatchDispatcher = snmpBatchDispatcher;
        this.snmpWorkerService = snmpWorkerService;
        this.legacyProperties = null;
        this.legacyExecutor = null;
        this.legacyTimeLimiterRegistry = null;
        this.legacyRetryRegistry = null;
        this.legacyMode = false;
    }

    public SnmpPollingService(
            SnmpDeviceRepository deviceRepository,
            SnmpObservedStateService observedStateService,
            SnmpProperties properties,
            ThreadPoolTaskExecutor executor,
            TimeLimiterRegistry timeLimiterRegistry,
            RetryRegistry retryRegistry
    ) {
        this.deviceRepository = deviceRepository;
        this.observedStateService = observedStateService;
        this.snmpBatchDispatcher = null;
        this.snmpWorkerService = null;
        this.legacyProperties = properties;
        this.legacyExecutor = executor;
        this.legacyTimeLimiterRegistry = timeLimiterRegistry;
        this.legacyRetryRegistry = retryRegistry;
        this.legacyMode = true;
    }

    @Override
    public List<SnmpDeviceSnapshot> pollEnabledDevices() {
        List<SnmpDevice> devices = deviceRepository.findByEnabledTrueOrderByIpAddressAsc();
        if (devices.isEmpty()) {
            return List.of();
        }

        List<SnmpDeviceSnapshot> snapshots = legacyMode
                ? pollEnabledDevicesLegacy(devices)
                : snmpBatchDispatcher.dispatch(devices);

        observedStateService.persistObservedStatus(devices, snapshots);
        observedStateService.persistInterfaces(snapshots);
        logCycleSummary(snapshots);
        return snapshots;
    }

    @Override
    public SnmpDeviceSnapshot pollSingleDevice(SnmpDevice device) {
        if (legacyMode) {
            return pollDeviceWithRetry(device);
        }
        try {
            return snmpWorkerService.pollDeviceAsync(device).get();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IntegrationTimeoutException(SOURCE, "SNMP single-device polling interrupted", interruptedException);
        } catch (ExecutionException executionException) {
            Throwable cause = executionException.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IntegrationUnavailableException(SOURCE, "SNMP single-device polling failed", cause);
        }
    }

    List<SnmpDeviceSnapshot> pollEnabledDevicesLegacy(List<SnmpDevice> devices) {
        return devices.stream()
                .map(this::pollDeviceWithRetry)
                .toList();
    }

    SnmpDeviceSnapshot pollDeviceWithRetry(SnmpDevice device) {
        if (!legacyMode) {
            return pollSingleDevice(device);
        }
        io.github.resilience4j.retry.Retry retry = legacyRetryRegistry.retry(RESILIENCE_NAME);
        try {
            return io.github.resilience4j.retry.Retry
                    .decorateCheckedSupplier(retry, () -> pollDeviceWithTimeout(device))
                    .get();
        } catch (Throwable exception) {
            return buildLegacyDownSnapshot(device, SnmpExceptionUtils.safeMessage(exception));
        }
    }

    SnmpDeviceSnapshot pollDeviceWithTimeout(SnmpDevice device) throws Exception {
        if (!legacyMode) {
            return pollSingleDevice(device);
        }
        String ip = device != null ? device.getIpAddress() : null;
        Integer port = device != null ? device.getSnmpPort() : null;
        String community = device != null ? device.getSnmpCommunity() : null;
        SnmpExceptionUtils.validateDeviceConfiguration(device, ip, port, community);
        try {
            return legacyTimeLimiterRegistry.timeLimiter(RESILIENCE_NAME)
                    .executeFutureSupplier(() -> CompletableFuture.supplyAsync(
                            () -> buildLegacyUpSnapshot(device),
                            legacyExecutor
                    ));
        } catch (Exception exception) {
            throw classifyRetryableException(device, exception);
        }
    }

    private RuntimeException classifyRetryableException(SnmpDevice device, Exception exception) {
        Throwable cause = unwrap(exception);
        String ip = device != null ? device.getIpAddress() : "unknown";
        if (cause instanceof IntegrationResponseException responseException) {
            return responseException;
        }
        if (cause instanceof IntegrationUnavailableException unavailableException) {
            return unavailableException;
        }
        if (cause instanceof IntegrationTimeoutException timeoutException) {
            return timeoutException;
        }
        if (cause instanceof java.util.concurrent.TimeoutException
                || cause instanceof java.util.concurrent.CancellationException
                || cause instanceof InterruptedException) {
            return new IntegrationTimeoutException(MonitoringConstants.SOURCE_SNMP, "SNMP timeout while polling " + ip, cause);
        }
        return SnmpExceptionUtils.classifyIoException(device, cause);
    }

    private SnmpDeviceSnapshot buildLegacyUpSnapshot(SnmpDevice device) {
        return SnmpDeviceSnapshot.builder()
                .ipAddress(device.getIpAddress())
                .hostId(device.getIpAddress())
                .hostName(device.getHostname() != null ? device.getHostname() : device.getIpAddress())
                .category(device.getCategory() != null ? device.getCategory() : MonitoringConstants.CATEGORY_SWITCH)
                .snmpPort(device.getSnmpPort() != null ? device.getSnmpPort() : legacyProperties.getDefaultPort())
                .status(MonitoringConstants.STATUS_UP)
                .deviceStatus(DeviceStatus.UP)
                .availability(1.0)
                .sysDescr("ok")
                .interfaces(List.of())
                .extraMetrics(Map.of())
                .collectedAtEpochSec(System.currentTimeMillis() / 1000)
                .build();
    }

    private SnmpDeviceSnapshot buildLegacyDownSnapshot(SnmpDevice device, String reason) {
        return SnmpDeviceSnapshot.builder()
                .ipAddress(device.getIpAddress())
                .hostId(device.getIpAddress())
                .hostName(device.getHostname() != null ? device.getHostname() : device.getIpAddress())
                .category(device.getCategory() != null ? device.getCategory() : MonitoringConstants.CATEGORY_SWITCH)
                .snmpPort(device.getSnmpPort() != null ? device.getSnmpPort() : legacyProperties.getDefaultPort())
                .status(MonitoringConstants.STATUS_DOWN)
                .deviceStatus(DeviceStatus.DOWN)
                .availability(0.0)
                .sysDescr(MonitoringConstants.UNKNOWN)
                .diagnosticReason(reason)
                .interfaces(List.of())
                .extraMetrics(Map.of())
                .collectedAtEpochSec(System.currentTimeMillis() / 1000)
                .build();
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null
                && (current instanceof java.lang.reflect.InvocationTargetException
                || current instanceof CompletionException
                || current instanceof RuntimeException)) {
            current = current.getCause();
        }
        return current;
    }

    private void logCycleSummary(List<SnmpDeviceSnapshot> snapshots) {
        long up = snapshots.stream().filter(s -> DeviceStatus.UP == s.getDeviceStatus()).count();
        long down = snapshots.stream().filter(s -> DeviceStatus.DOWN == s.getDeviceStatus()).count();
        long degraded = snapshots.stream().filter(s -> DeviceStatus.DEGRADED == s.getDeviceStatus()).count();
        long unknown = snapshots.stream().filter(s -> DeviceStatus.UNKNOWN == s.getDeviceStatus()).count();
        long interfaces = snapshots.stream()
                .map(SnmpDeviceSnapshot::getInterfaces)
                .filter(list -> list != null)
                .mapToLong(List::size)
                .sum();
        log.info("SNMP cycle completed: total={} up={} down={} degraded={} unknown={} interfaces={}",
                snapshots.size(), up, down, degraded, unknown, interfaces);
    }
}
