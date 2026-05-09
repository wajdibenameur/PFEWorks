package tn.iteam.integration;

import reactor.core.publisher.Mono;

public interface ZkBioIntegrationOperations extends AsyncIntegrationService {

    default Mono<Void> refreshAttendanceAsync() {
        return Mono.fromRunnable(this::refreshAttendance);
    }

    default Mono<Void> refreshAllAndPublishAsync() {
        return Mono.fromRunnable(this::refreshAllAndPublish);
    }
}
