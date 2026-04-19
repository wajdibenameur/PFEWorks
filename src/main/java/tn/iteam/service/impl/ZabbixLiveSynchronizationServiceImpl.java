package tn.iteam.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.zabbix.ZabbixClient;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.ZabbixLiveSynchronizationService;
import tn.iteam.service.ZabbixMetricsService;
import tn.iteam.service.ZabbixProblemService;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class ZabbixLiveSynchronizationServiceImpl implements ZabbixLiveSynchronizationService {

    private static final Logger log = LoggerFactory.getLogger(ZabbixLiveSynchronizationServiceImpl.class);

    private final ZabbixClient zabbixClient;
    private final ZabbixProblemService zabbixProblemService;
    private final ZabbixMetricsService zabbixMetricsService;
    private final SourceAvailabilityService availabilityService;

    private final ReentrantLock syncLock = new ReentrantLock();

    @Value("${zabbix.scheduler.problems.rate:30000}")
    private long problemsRateMs;

    @Value("${zabbix.scheduler.metrics.rate:60000}")
    private long metricsRateMs;

    @Value("${zabbix.sync.dedupe-window-ms:5000}")
    private long dedupeWindowMs;

    @Value("${zabbix.retry-backoff-ms:120000}")
    private long retryBackoffMs;

    private volatile Instant lastProblemsSyncAt;
    private volatile Instant lastMetricsSyncAt;

    @Override
    public void synchronizeForStartup() {
        synchronizeInternal("startup", true, true, false);
    }

    @Override
    public void synchronizeForProblemsTick() {
        synchronizeInternal("problems tick", true, false, true);
    }

    @Override
    public void synchronizeForMetricsTick() {
        synchronizeInternal("metrics tick", false, true, true);
    }

    private void synchronizeInternal(String trigger, boolean requireProblems, boolean requireMetrics, boolean allowSkipWhenBusy) {
        if (allowSkipWhenBusy && !syncLock.tryLock()) {
            log.info("Skipping {} because another Zabbix live synchronization is already running", trigger);
            return;
        }

        if (!allowSkipWhenBusy) {
            syncLock.lock();
        }

        try {
            Instant now = Instant.now();

            boolean shouldSyncProblems = requireProblems || isDue(lastProblemsSyncAt, problemsRateMs, now);
            boolean shouldSyncMetrics = requireMetrics || isDue(lastMetricsSyncAt, metricsRateMs, now);

            if (!shouldSyncProblems && !shouldSyncMetrics) {
                log.debug("Skipping {} because Zabbix data is already fresh enough", trigger);
                return;
            }

            if (!availabilityService.shouldAttempt("ZABBIX", retryBackoffMs)) {
                log.debug(
                        "Skipping {} because Zabbix is marked unavailable and retry backoff ({} ms) is still active",
                        trigger,
                        retryBackoffMs
                );
                return;
            }

            JsonNode hosts = zabbixClient.getHosts();
            log.info("Running shared Zabbix live synchronization for {}", trigger);

            if (shouldSyncProblems) {
                zabbixProblemService.synchronizeActiveProblemsFromZabbix(hosts);
                if (availabilityService.isAvailable("ZABBIX")) {
                    lastProblemsSyncAt = Instant.now();
                }
            }

            if (shouldSyncMetrics) {
                zabbixMetricsService.fetchAndSaveMetrics(hosts);
                if (availabilityService.isAvailable("ZABBIX")) {
                    lastMetricsSyncAt = Instant.now();
                }
            }
        } finally {
            syncLock.unlock();
        }
    }

    private boolean isDue(Instant lastSyncAt, long intervalMs, Instant now) {
        if (lastSyncAt == null) {
            return true;
        }

        long elapsedMs = now.toEpochMilli() - lastSyncAt.toEpochMilli();
        long thresholdMs = Math.max(0, intervalMs - dedupeWindowMs);
        return elapsedMs >= thresholdMs;
    }
}
