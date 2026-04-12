package tn.iteam.exception;

public class ObserviumConnectionException extends RuntimeException {

    public ObserviumConnectionException(String message) {
        super(message);
    }

    public ObserviumConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}