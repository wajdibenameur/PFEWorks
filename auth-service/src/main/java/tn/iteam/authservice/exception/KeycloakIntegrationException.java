package tn.iteam.authservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a downstream Keycloak API call fails unexpectedly.
 */
public class KeycloakIntegrationException extends RuntimeException {

    private final HttpStatus status;

    public KeycloakIntegrationException(String message) {
        super(message);
        this.status = HttpStatus.BAD_GATEWAY;
    }

    public KeycloakIntegrationException(String message, Throwable cause) {
        super(message, cause);
        this.status = HttpStatus.BAD_GATEWAY;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
