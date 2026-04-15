package tn.iteam.exception;

import org.springframework.http.HttpStatus;

public class IntegrationUnavailableException extends IntegrationException {

    public IntegrationUnavailableException(String source, String message) {
        this(source, message, null);
    }

    public IntegrationUnavailableException(String source, String message, Throwable cause) {
        super(source, source + "_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }
}
