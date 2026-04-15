package tn.iteam.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class SourceAvailabilityDTO {
    String source;
    boolean available;
    String status;
    String message;
    String lastError;
    Instant lastFailureAt;
    Instant timestamp;
}
