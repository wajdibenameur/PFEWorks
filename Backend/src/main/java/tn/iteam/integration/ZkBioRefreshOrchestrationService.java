package tn.iteam.integration;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Keeps the ZKBio refresh orchestration consistent across startup warmup and
 * manual collection endpoints.
 *
 * Contract: attendance refresh runs after the main ZKBio monitoring refresh.
 * This backend intentionally treats attendance/device/status refresh as
 * dependent on the refreshed ZKBio monitoring state and snapshot context.
 */
@Service
public class ZkBioRefreshOrchestrationService {

    private final ZkBioIntegrationOperations zkBioIntegrationOperations;

    public ZkBioRefreshOrchestrationService(ZkBioIntegrationOperations zkBioIntegrationOperations) {
        this.zkBioIntegrationOperations = zkBioIntegrationOperations;
    }

    /**
     * Refreshes ZKBio monitoring datasets first, then refreshes attendance and
     * device/status datasets that are intentionally sequenced after that state.
     */
    public Mono<Void> refreshMonitoringAndAttendanceAsync() {
        // Attendance/device/status refresh is intentionally sequenced after the
        // main monitoring refresh so both startup warmup and manual collection
        // reuse the same refreshed ZKBio state/context before publication.
        return zkBioIntegrationOperations.refreshAsync()
                .then(zkBioIntegrationOperations.refreshAttendanceAsync());
    }
}
