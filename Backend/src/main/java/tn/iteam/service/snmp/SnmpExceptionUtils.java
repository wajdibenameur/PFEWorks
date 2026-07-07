package tn.iteam.service.snmp;

import tn.iteam.domain.SnmpDevice;
import tn.iteam.exception.IntegrationResponseException;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;
import tn.iteam.util.MonitoringConstants;

import java.io.IOException;
import java.io.InterruptedIOException;

final class SnmpExceptionUtils {

    private SnmpExceptionUtils() {
    }

    static RuntimeException classifyIoException(SnmpDevice device, Throwable throwable) {
        String ip = device != null ? device.getIpAddress() : "unknown";
        if (throwable instanceof IntegrationResponseException responseException) {
            return responseException;
        }
        if (throwable instanceof IntegrationUnavailableException unavailableException) {
            return unavailableException;
        }
        if (throwable instanceof IntegrationTimeoutException timeoutException) {
            return timeoutException;
        }
        if (throwable instanceof java.net.SocketTimeoutException
                || throwable instanceof InterruptedIOException
                || throwable instanceof InterruptedException) {
            return new IntegrationTimeoutException(MonitoringConstants.SOURCE_SNMP, "SNMP timeout while polling " + ip, throwable);
        }
        if (throwable instanceof IOException) {
            return new IntegrationUnavailableException(MonitoringConstants.SOURCE_SNMP, "SNMP temporary network error while polling " + ip, throwable);
        }
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IntegrationUnavailableException(MonitoringConstants.SOURCE_SNMP, "SNMP temporary network error while polling " + ip, throwable);
    }

    static void validateDeviceConfiguration(SnmpDevice device, String ip, Integer port, String community) {
        if (device != null && Boolean.FALSE.equals(device.getEnabled())) {
            throw new IntegrationResponseException(MonitoringConstants.SOURCE_SNMP, "SNMP device is disabled");
        }
        if (ip == null || ip.isBlank()) {
            throw new IntegrationResponseException(MonitoringConstants.SOURCE_SNMP, "SNMP device IP is missing");
        }
        if (port == null || port <= 0) {
            throw new IntegrationResponseException(MonitoringConstants.SOURCE_SNMP, "SNMP port is invalid for " + ip);
        }
        if (community == null || community.isBlank()) {
            throw new IntegrationResponseException(MonitoringConstants.SOURCE_SNMP, "SNMP community is missing for " + ip);
        }
    }

    static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return throwable != null ? throwable.getClass().getSimpleName() : "unknown";
        }
        return throwable.getMessage();
    }
}
