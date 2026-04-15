package tn.iteam.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tn.iteam.dto.ApiErrorResponse;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IntegrationException.class)
    public ResponseEntity<ApiErrorResponse> handleIntegrationException(
            IntegrationException ex,
            HttpServletRequest request
    ) {
        if (ex instanceof IntegrationTimeoutException || ex instanceof IntegrationUnavailableException) {
            log.warn("Integration error from {}: {}", ex.getSource(), ex.getMessage());
        } else {
            log.error("Integration error from {}: {}", ex.getSource(), ex.getMessage(), ex);
        }

        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiErrorResponse.builder()
                        .timestamp(Instant.now())
                        .status(ex.getHttpStatus().value())
                        .errorCode(ex.getErrorCode())
                        .message(ex.getMessage())
                        .source(ex.getSource())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAllExceptions(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);

        return ResponseEntity.internalServerError()
                .body(ApiErrorResponse.builder()
                        .timestamp(Instant.now())
                        .status(500)
                        .errorCode("INTERNAL_ERROR")
                        .message("Internal server error")
                        .source("SYSTEM")
                        .path(request.getRequestURI())
                        .build());
    }
}
