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
    private static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";
    private static final String INTERNAL_ERROR_MESSAGE = "Internal server error";
    private static final String SYSTEM_SOURCE = "SYSTEM";
    private static final String TICKETING_SOURCE = "TICKETING";
    private static final int INTERNAL_SERVER_ERROR_STATUS = 500;

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

        return buildErrorResponse(
                ResponseEntity.status(ex.getHttpStatus()),
                ex.getHttpStatus().value(),
                ex.getErrorCode(),
                ex.getMessage(),
                ex.getSource(),
                request
        );
    }

    @ExceptionHandler(TicketingException.class)
    public ResponseEntity<ApiErrorResponse> handleTicketingException(
            TicketingException ex,
            HttpServletRequest request
    ) {
        log.warn("Ticketing error: {}", ex.getMessage());

        return buildErrorResponse(
                ResponseEntity.status(ex.getHttpStatus()),
                ex.getHttpStatus().value(),
                ex.getErrorCode(),
                ex.getMessage(),
                TICKETING_SOURCE,
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAllExceptions(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);

        return buildErrorResponse(
                ResponseEntity.internalServerError(),
                INTERNAL_SERVER_ERROR_STATUS,
                INTERNAL_ERROR_CODE,
                INTERNAL_ERROR_MESSAGE,
                SYSTEM_SOURCE,
                request
        );
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(
            ResponseEntity.BodyBuilder responseBuilder,
            int status,
            String errorCode,
            String message,
            String source,
            HttpServletRequest request
    ) {
        return responseBuilder.body(ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .source(source)
                .path(request.getRequestURI())
                .build());
    }
}
