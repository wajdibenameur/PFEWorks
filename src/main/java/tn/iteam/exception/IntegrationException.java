package tn.iteam.exception;

import org.springframework.http.HttpStatus;

public abstract class IntegrationException extends RuntimeException {

    private final String source;
    private final String errorCode;
    private final HttpStatus httpStatus;

    protected IntegrationException(
            String source,
            String errorCode,
            HttpStatus httpStatus,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.source = source;
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getSource() {
        return source;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
