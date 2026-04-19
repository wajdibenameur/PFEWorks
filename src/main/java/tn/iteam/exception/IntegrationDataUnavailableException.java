package tn.iteam.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when integration returns no data (live failed + fallback unavailable).
 * This allows the monitoring aggregation layer to properly mark the source as degraded.
 */
public class IntegrationDataUnavailableException extends IntegrationException {

    protected IntegrationDataUnavailableException(String source, String message) {
        super(source, buildErrorCode(source, "DATA_UNAVAILABLE"), HttpStatus.SERVICE_UNAVAILABLE, message, null);
    }

    public static IntegrationDataUnavailableException forObservium(String message) {
        return new IntegrationDataUnavailableException("OBSERVIUM", message);
    }

    public static IntegrationDataUnavailableException forZkBio(String message) {
        return new IntegrationDataUnavailableException("ZKBIO", message);
    }

    public static IntegrationDataUnavailableException forZabbix(String message) {
        return new IntegrationDataUnavailableException("ZABBIX", message);
    }
}