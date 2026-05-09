package tn.iteam.authservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a user with the same username or email already exists in Keycloak.
 */
public class UserAlreadyExistsException extends RuntimeException {

    private final HttpStatus status;

    public UserAlreadyExistsException(String message) {
        super(message);
        this.status = HttpStatus.CONFLICT;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
