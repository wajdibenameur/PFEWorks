package tn.iteam.service;

import tn.iteam.dto.SourceAvailabilityDTO;

import java.time.Instant;
import java.util.List;

public interface SourceAvailabilityService {

    void markAvailable(String source);

    void markDegraded(String source, String errorMessage);

    void markUnavailable(String source, String errorMessage);

    boolean isAvailable(String source);

    boolean isDegraded(String source);

    String getStatus(String source);

    String getLastError(String source);

    Instant getLastFailureAt(String source);

    boolean shouldAttempt(String source, long retryBackoffMs);

    boolean isRetryCooldownActive(String source, long retryBackoffMs);

    List<SourceAvailabilityDTO> getAll();

    SourceAvailabilityDTO get(String source);
}
