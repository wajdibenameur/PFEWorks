package tn.iteam.exception;

import org.springframework.http.HttpStatus;

public class IntegrationResponseException extends IntegrationException {

    public IntegrationResponseException(String source, String message) {
        this(source, message, null);
    }

    public IntegrationResponseException(String source, String message, Throwable cause) {
        super(source, source + "_INVALID_RESPONSE", HttpStatus.BAD_GATEWAY, message, cause);
    }
}
