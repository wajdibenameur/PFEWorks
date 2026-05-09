package tn.iteam.service.impl;

import org.springframework.stereotype.Service;
import tn.iteam.dto.SourceAvailabilityDTO;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.websocket.MonitoringWebSocketPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SourceAvailabilityServiceImpl implements SourceAvailabilityService {

    private static final String ZABBIX = "ZABBIX";
    private static final String OBSERVIUM = "OBSERVIUM";
    private static final String ZKBIO = "ZKBIO";
    private static final String CAMERA = "CAMERA";
    private static final String DATABASE = "DATABASE";
    private static final String UNKNOWN = "UNKNOWN";
    private static final String UNKNOWN_INTEGRATION_ERROR = "Unknown integration error";
    private static final String AVAILABLE = "AVAILABLE";
    private static final String DEGRADED = "DEGRADED";
    private static final String UNAVAILABLE = "UNAVAILABLE";

    private static final List<String> KNOWN_SOURCES = List.of(ZABBIX, OBSERVIUM, ZKBIO, CAMERA, DATABASE);

    private final Map<String, AvailabilityState> states = new ConcurrentHashMap<>();
    private final MonitoringWebSocketPublisher publisher;

    public SourceAvailabilityServiceImpl(MonitoringWebSocketPublisher publisher) {
        this.publisher = publisher;
        for (String source : KNOWN_SOURCES) {
            states.put(source, new AvailabilityState(AVAILABLE, null, null));
        }
    }

    @Override
    public void markAvailable(String source) {
        String normalized = normalize(source);
        updateState(normalized, new AvailabilityState(AVAILABLE, null, null), Instant.now());
    }

    @Override
    public void markDegraded(String source, String errorMessage) {
        String normalized = normalize(source);
        String safeMessage = errorMessage == null || errorMessage.isBlank()
                ? UNKNOWN_INTEGRATION_ERROR
                : errorMessage;
        updateState(normalized, new AvailabilityState(DEGRADED, safeMessage, Instant.now()), Instant.now());
    }

    @Override
    public void markUnavailable(String source, String errorMessage) {
        String normalized = normalize(source);
        String safeMessage = errorMessage == null || errorMessage.isBlank()
                ? UNKNOWN_INTEGRATION_ERROR
                : errorMessage;
        Instant failureAt = Instant.now();
        updateState(normalized, new AvailabilityState(UNAVAILABLE, safeMessage, failureAt), failureAt);
    }

    @Override
    public boolean isAvailable(String source) {
        return AVAILABLE.equals(getState(source).status());
    }

    @Override
    public boolean isDegraded(String source) {
        return DEGRADED.equals(getState(source).status());
    }

    @Override
    public String getStatus(String source) {
        return getState(source).status();
    }

    @Override
    public String getLastError(String source) {
        return getState(source).lastError();
    }

    @Override
    public Instant getLastFailureAt(String source) {
        return getState(source).lastFailureAt();
    }

    @Override
    public boolean shouldAttempt(String source, long retryBackoffMs) {
        return !isRetryCooldownActive(source, retryBackoffMs);
    }

    @Override
    public boolean isRetryCooldownActive(String source, long retryBackoffMs) {
        if (retryBackoffMs <= 0 || isAvailable(source)) {
            return false;
        }

        Instant lastFailureAt = getLastFailureAt(source);
        if (lastFailureAt == null) {
            return false;
        }

        long elapsedMs = Instant.now().toEpochMilli() - lastFailureAt.toEpochMilli();
        return elapsedMs < retryBackoffMs;
    }

    @Override
    public List<SourceAvailabilityDTO> getAll() {
        return KNOWN_SOURCES.stream().map(this::get).toList();
    }

    @Override
    public SourceAvailabilityDTO get(String source) {
        AvailabilityState state = getState(source);
        return toDto(normalize(source), state, state.lastFailureAt());
    }

    private AvailabilityState getState(String source) {
        return states.getOrDefault(normalize(source), new AvailabilityState(AVAILABLE, null, null));
    }

    private String normalize(String source) {
        return source == null ? UNKNOWN : source.trim().toUpperCase();
    }

    private SourceAvailabilityDTO toDto(String source, AvailabilityState state, Instant timestamp) {
        return SourceAvailabilityDTO.builder()
                .source(source)
                .available(AVAILABLE.equals(state.status()))
                .status(state.status())
                .message(AVAILABLE.equals(state.status()) ? null : state.lastError())
                .lastError(state.lastError())
                .lastFailureAt(state.lastFailureAt())
                .timestamp(timestamp)
                .build();
    }

    private void updateState(String source, AvailabilityState next, Instant eventTimestamp) {
        AvailabilityState previous = getState(source);
        states.put(source, next);

        if (hasChanged(previous, next)) {
            publisher.publishSourceAvailability(toDto(source, next, eventTimestamp));
        }
    }

    private boolean hasChanged(AvailabilityState previous, AvailabilityState next) {
        if (!previous.status().equals(next.status())) {
            return true;
        }
        if (previous.lastError() == null) {
            return next.lastError() != null;
        }
        return !previous.lastError().equals(next.lastError());
    }

    private record AvailabilityState(String status, String lastError, Instant lastFailureAt) {
    }
}
