package tn.iteam.exception;

import org.springframework.http.HttpStatus;

public class IntegrationTimeoutException extends IntegrationException {

    public IntegrationTimeoutException(String source, String message, Throwable cause) {
        super(source, source + "_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT, message, cause);
    }
}
