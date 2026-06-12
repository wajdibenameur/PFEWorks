package tn.iteam.service.snmp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.snmp.SnmpDeviceSnapshot;
import tn.iteam.config.SnmpProperties;
import tn.iteam.domain.SnmpDevice;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnmpBatchDispatcher {

    private static final String SOURCE = "SNMP";

    private final SnmpWorkerService snmpWorkerService;
    private final SnmpProperties snmpProperties;

    public List<SnmpDeviceSnapshot> dispatch(List<SnmpDevice> devices) {
        if (devices == null || devices.isEmpty()) {
            return List.of();
        }

        List<SnmpDeviceSnapshot> snapshots = new ArrayList<>(devices.size());
        int batchSize = Math.max(1, snmpProperties.getBatchSize());
        for (int start = 0; start < devices.size(); start += batchSize) {
            int end = Math.min(start + batchSize, devices.size());
            snapshots.addAll(dispatchBatch(devices.subList(start, end), start / batchSize + 1));
        }
        return List.copyOf(snapshots);
    }

    private List<SnmpDeviceSnapshot> dispatchBatch(List<SnmpDevice> batch, int batchNumber) {
        log.info("SNMP batch start batch={} size={}", batchNumber, batch.size());
        List<CompletableFuture<SnmpDeviceSnapshot>> futures = batch.stream()
                .map(snmpWorkerService::pollDeviceAsync)
                .toList();

        CompletableFuture<Void> batchFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            batchFuture.get();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IntegrationTimeoutException(SOURCE, "SNMP batch interrupted", interruptedException);
        } catch (ExecutionException executionException) {
            log.warn("SNMP batch {} completed with worker error: {}", batchNumber, executionException.getMessage());
        }

        List<SnmpDeviceSnapshot> snapshots = futures.stream()
                .map(this::safeResolve)
                .toList();
        log.info("SNMP batch done batch={} size={}", batchNumber, snapshots.size());
        return snapshots;
    }

    private SnmpDeviceSnapshot safeResolve(CompletableFuture<SnmpDeviceSnapshot> future) {
        try {
            return future.get();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IntegrationTimeoutException(SOURCE, "SNMP worker interrupted", interruptedException);
        } catch (ExecutionException executionException) {
            Throwable cause = executionException.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IntegrationUnavailableException(SOURCE, "SNMP worker failed", cause);
        }
    }
}
