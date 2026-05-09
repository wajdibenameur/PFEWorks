package tn.iteam.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ResilienceLoggingConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceLoggingConfig.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    public ResilienceLoggingConfig(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry
    ) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
    }

    @PostConstruct
    void registerEventListeners() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::registerCircuitBreakerLogging);
        circuitBreakerRegistry.getEventPublisher()
                .onEntryAdded(event -> registerCircuitBreakerLogging(event.getAddedEntry()));

        retryRegistry.getAllRetries().forEach(this::registerRetryLogging);
        retryRegistry.getEventPublisher()
                .onEntryAdded(event -> registerRetryLogging(event.getAddedEntry()));
    }

    private void registerCircuitBreakerLogging(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn(
                        "Circuit breaker {} transitioned {}",
                        event.getCircuitBreakerName(),
                        event.getStateTransition()
                ))
                .onCallNotPermitted(event -> log.warn(
                        "Circuit breaker {} rejected a call while OPEN or HALF_OPEN",
                        event.getCircuitBreakerName()
                ))
                .onFailureRateExceeded(event -> log.warn(
                        "Circuit breaker {} exceeded failure rate threshold: {}",
                        event.getCircuitBreakerName(),
                        event.getFailureRate()
                ))
                .onSlowCallRateExceeded(event -> log.warn(
                        "Circuit breaker {} exceeded slow call rate threshold: {}",
                        event.getCircuitBreakerName(),
                        event.getSlowCallRate()
                ));
    }

    private void registerRetryLogging(Retry retry) {
        retry.getEventPublisher()
                .onRetry(event -> log.warn(
                        "Retry {} attempt {} after {} ms due to {}",
                        event.getName(),
                        event.getNumberOfRetryAttempts(),
                        event.getWaitInterval().toMillis(),
                        describeThrowable(event.getLastThrowable())
                ))
                .onError(event -> log.warn(
                        "Retry {} exhausted after {} attempts due to {}",
                        event.getName(),
                        event.getNumberOfRetryAttempts(),
                        describeThrowable(event.getLastThrowable())
                ))
                .onIgnoredError(event -> log.debug(
                        "Retry {} ignored non-retryable error {}",
                        event.getName(),
                        describeThrowable(event.getLastThrowable())
                ));
    }

    private String describeThrowable(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : throwable.getClass().getSimpleName() + ": " + message;
    }
}
