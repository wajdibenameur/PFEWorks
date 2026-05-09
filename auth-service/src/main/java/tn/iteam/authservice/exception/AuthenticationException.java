package tn.iteam.authservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when Keycloak rejects authentication credentials.
 */
public class AuthenticationException extends RuntimeException {

    private final HttpStatus status;

    public AuthenticationException(String message) {
        super(message);
        this.status = HttpStatus.UNAUTHORIZED;
    }

    public AuthenticationException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
