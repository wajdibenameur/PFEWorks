package tn.iteam.exception;

public class ZabbixConnectionException extends IntegrationUnavailableException {

    public ZabbixConnectionException(String message) {
        super("ZABBIX", message);
    }

    public ZabbixConnectionException(String message, Throwable cause) {
        super("ZABBIX", message, cause);
    }
}
