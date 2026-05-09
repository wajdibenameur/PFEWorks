package tn.iteam.exception;

import org.springframework.http.HttpStatus;

public class IntegrationDataUnavailableException extends IntegrationException {

    private static final String ERROR_CODE_SUFFIX = "DATA_UNAVAILABLE";

    public IntegrationDataUnavailableException(String source, String message) {
        super(source, buildErrorCode(source, ERROR_CODE_SUFFIX), HttpStatus.SERVICE_UNAVAILABLE, message, null);
    }

    public IntegrationDataUnavailableException(String source, String message, Throwable cause) {
        super(source, buildErrorCode(source, ERROR_CODE_SUFFIX), HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }

    public static IntegrationDataUnavailableException forSource(String source, String message) {
        return new IntegrationDataUnavailableException(source, message);
    }

    public static IntegrationDataUnavailableException forObservium(String message) {
        return forSource("OBSERVIUM", message);
    }

    public static IntegrationDataUnavailableException forZabbix(String message) {
        return forSource("ZABBIX", message);
    }

    public static IntegrationDataUnavailableException forZkBio(String message) {
        return forSource("ZKBIO", message);
    }

    public static IntegrationDataUnavailableException forCamera(String message) {
        return forSource("CAMERA", message);
    }
}