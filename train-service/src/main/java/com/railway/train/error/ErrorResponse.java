package com.railway.train.error;

import org.springframework.http.HttpStatus;

import java.time.Instant;

/** Standard error body returned by every service. */
public record ErrorResponse(String timestamp, int status, String error, String message, String path) {

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(Instant.now().toString(), status.value(), status.getReasonPhrase(), message, path);
    }
}
