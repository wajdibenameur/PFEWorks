package tn.iteam.integration;

import reactor.core.publisher.Mono;

public interface AsyncIntegrationService extends IntegrationService {

    default Mono<Void> refreshAsync() {
        return Mono.fromRunnable(this::refresh);
    }

    default Mono<Void> refreshHostsAsync() {
        return Mono.fromRunnable(this::refreshHosts);
    }

    default Mono<Void> refreshProblemsAsync() {
        return Mono.fromRunnable(this::refreshProblems);
    }

    default Mono<Void> refreshMetricsAsync() {
        return Mono.fromRunnable(this::refreshMetrics);
    }
}
