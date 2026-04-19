package tn.iteam.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.iteam.adapter.observium.ObserviumAdapter;
import tn.iteam.dto.ObserviumProblemDTO;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.util.MonitoringConstants;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ObserviumScheduler {

    private final ObserviumAdapter observiumAdapter;
    private final SourceAvailabilityService availabilityService;

    @Value("${observium.retry-backoff-ms:120000}")
    private long retryBackoffMs;

    @Scheduled(
            fixedRateString = "${observium.scheduler.problems.rate:60000}",
            initialDelayString = "${observium.scheduler.problems.initial-delay:45000}"
    )
    public void refreshProblems() {
        if (!availabilityService.shouldAttempt(MonitoringConstants.SOURCE_OBSERVIUM, retryBackoffMs)) {
            log.debug(
                    "Skipping Observium problems refresh because source is marked unavailable and retry backoff ({} ms) is still active",
                    retryBackoffMs
            );
            return;
        }

        List<ObserviumProblemDTO> problems = observiumAdapter.fetchProblemsAndSave();
        log.debug("Scheduled: refreshed {} Observium problems", problems.size());
    }
}
