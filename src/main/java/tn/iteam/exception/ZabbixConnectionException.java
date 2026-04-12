package tn.iteam.exception;

public class ZabbixConnectionException extends RuntimeException {

    public ZabbixConnectionException(String message) {
        super(message);
    }

    public ZabbixConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}