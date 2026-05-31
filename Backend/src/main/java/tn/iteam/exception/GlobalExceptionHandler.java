package tn.iteam.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tn.iteam.auth.exception.AuthenticationException;
import tn.iteam.auth.exception.KeycloakIntegrationException;
import tn.iteam.auth.exception.UserAlreadyExistsException;
import tn.iteam.auth.service.AuthCookieService;
import tn.iteam.dto.ApiError;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Optional<AuthCookieService> authCookieService;

    public GlobalExceptionHandler(Optional<AuthCookieService> authCookieService) {
        this.authCookieService = authCookieService;
    }

    @ExceptionHandler(IntegrationException.class)
    public ResponseEntity<ApiError> handleIntegrationException(
            IntegrationException ex,
            HttpServletRequest request
    ) {
        if (ex instanceof IntegrationTimeoutException || ex instanceof IntegrationUnavailableException) {
            log.warn("Integration error from {}: {}", ex.getSource(), ex.getMessage());
        } else {
            log.error("Integration error from {}: {}", ex.getSource(), ex.getMessage(), ex);
        }
        return buildErrorResponse(ex.getHttpStatus(), ex.getMessage(), request, null, null);
    }

    @ExceptionHandler(TicketingException.class)
    public ResponseEntity<ApiError> handleTicketingException(
            TicketingException ex,
            HttpServletRequest request
    ) {
        log.warn("Ticketing error: {}", ex.getMessage());
        return buildErrorResponse(ex.getHttpStatus(), ex.getMessage(), request, null, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request
    ) {
        log.warn("Authentication error on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(
                ex.getStatus(),
                ex.getMessage(),
                request,
                shouldExpireRefreshCookie(request) ? expiredRefreshCookieHeader() : null,
                null
        );
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleUserAlreadyExistsException(
            UserAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        log.warn("User conflict on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(ex.getStatus(), ex.getMessage(), request, null, null);
    }

    @ExceptionHandler(KeycloakIntegrationException.class)
    public ResponseEntity<ApiError> handleKeycloakIntegrationException(
            KeycloakIntegrationException ex,
            HttpServletRequest request
    ) {
        log.error("Identity provider integration failure on {} {}: {}",
                request.getMethod(),
                request.getRequestURI(),
                rootCauseMessage(ex));
        return buildErrorResponse(
                ex.getStatus(),
                "Identity service is temporarily unavailable. Please retry.",
                request,
                null,
                null
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            validationErrors.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("Validation error on {} {}: {} field(s)", request.getMethod(), request.getRequestURI(), validationErrors.size());
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                request,
                null,
                validationErrors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolationException(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        log.warn("Constraint violation on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        log.warn("Method not allowed on {} {}", request.getMethod(), request.getRequestURI());
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "HTTP method is not supported for this endpoint.",
                request,
                null,
                null
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        log.warn("Data conflict on {} {}: {}", request.getMethod(), request.getRequestURI(), rootCauseMessage(ex));
        return buildErrorResponse(HttpStatus.CONFLICT, "Request conflicts with existing data.", request, null, null);
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ApiError> handleAuthenticationCredentialsNotFoundException(
            AuthenticationCredentialsNotFoundException ex,
            HttpServletRequest request
    ) {
        log.warn("Unauthorized access on {} {}", request.getMethod(), request.getRequestURI());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication is required.", request, null, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        log.warn("Access denied on {} {}", request.getMethod(), request.getRequestURI());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.", request, null, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAllExceptions(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected server error occurred.", request, null, null);
    }

    private ResponseEntity<ApiError> buildErrorResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            String setCookieHeader,
            Map<String, String> validationErrors
    ) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (setCookieHeader != null && !setCookieHeader.isBlank()) {
            builder.header(HttpHeaders.SET_COOKIE, setCookieHeader);
        }
        return builder.body(ApiError.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .validationErrors(validationErrors == null || validationErrors.isEmpty() ? null : validationErrors)
                .build());
    }

    private boolean shouldExpireRefreshCookie(HttpServletRequest request) {
        String uri = request != null ? request.getRequestURI() : null;
        if (uri == null) {
            return false;
        }
        return uri.contains("/api/auth/refresh") || uri.contains("/api/auth/logout");
    }

    private String expiredRefreshCookieHeader() {
        return authCookieService
                .map(cookieService -> cookieService.buildExpiredRefreshCookie().toString())
                .orElse("");
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
