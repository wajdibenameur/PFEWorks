package tn.iteam.exception;

import org.springframework.http.HttpStatus;

public class TicketingException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;

    public TicketingException(HttpStatus httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
