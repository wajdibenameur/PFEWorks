package tn.iteam.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class ApiErrorResponse {
    Instant timestamp;
    int status;
    String errorCode;
    String message;
    String source;
    String path;
}
