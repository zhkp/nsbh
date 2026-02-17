package com.kp.nsbh.api;

import com.kp.nsbh.api.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                ex.getReason() == null ? status.getReasonPhrase() : ex.getReason(),
                request.getRequestURI(),
                RequestIdSupport.currentRequestId()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                "Validation failed",
                request.getRequestURI(),
                RequestIdSupport.currentRequestId()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnknown(Exception ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                "Internal server error",
                request.getRequestURI(),
                RequestIdSupport.currentRequestId()
        ));
    }
}
