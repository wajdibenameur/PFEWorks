package tn.iteam.exception;

public class ObserviumConnectionException extends IntegrationUnavailableException {

    public ObserviumConnectionException(String message) {
        super("OBSERVIUM", message);
    }

    public ObserviumConnectionException(String message, Throwable cause) {
        super("OBSERVIUM", message, cause);
    }
}
