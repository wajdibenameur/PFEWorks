package tn.iteam.exception;

import org.springframework.http.HttpStatus;

public class IntegrationUnavailableException extends IntegrationException {

    private static final String ERROR_CODE_SUFFIX = "UNAVAILABLE";

    public IntegrationUnavailableException(String source, String message) {
        this(source, message, null);
    }

    public IntegrationUnavailableException(String source, String message, Throwable cause) {
        super(source, buildErrorCode(source, ERROR_CODE_SUFFIX), HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }
}
