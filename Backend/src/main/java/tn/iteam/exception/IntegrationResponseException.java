package tn.iteam.exception;

import org.springframework.http.HttpStatus;

public class IntegrationResponseException extends IntegrationException {

    private static final String ERROR_CODE_SUFFIX = "INVALID_RESPONSE";

    public IntegrationResponseException(String source, String message) {
        this(source, message, null);
    }

    public IntegrationResponseException(String source, String message, Throwable cause) {
        super(source, buildErrorCode(source, ERROR_CODE_SUFFIX), HttpStatus.BAD_GATEWAY, message, cause);
    }
}
