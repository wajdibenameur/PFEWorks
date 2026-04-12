package tn.iteam.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import tn.iteam.domain.ApiResponse;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ZabbixConnectionException.class)
    public ResponseEntity<ApiResponse<Void>> handleZabbixException(ZabbixConnectionException ex) {

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .errorCode("ZABBIX_DOWN")
                        .source("ZABBIX")
                        .build());
    }

    @ExceptionHandler(ObserviumConnectionException.class)
    public ResponseEntity<ApiResponse<Void>> handleObserviumException(ObserviumConnectionException ex) {

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .errorCode("OBSERVIUM_DOWN")
                        .source("OBSERVIUM")
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllExceptions(Exception ex) {

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .message("Internal server error")
                        .errorCode("INTERNAL_ERROR")
                        .source("SYSTEM")
                        .build());
    }
}