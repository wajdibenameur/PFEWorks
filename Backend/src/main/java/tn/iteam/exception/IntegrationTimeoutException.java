package tn.iteam.exception;

import org.springframework.http.HttpStatus;

public class IntegrationTimeoutException extends IntegrationException {

    private static final String ERROR_CODE_SUFFIX = "TIMEOUT";

    public IntegrationTimeoutException(String source, String message, Throwable cause) {
        super(source, buildErrorCode(source, ERROR_CODE_SUFFIX), HttpStatus.GATEWAY_TIMEOUT, message, cause);
    }
}
