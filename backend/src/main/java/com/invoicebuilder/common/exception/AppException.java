package com.invoicebuilder.common.exception;

import java.util.List;

/**
 * Application-level exception carrying a structured {@link ErrorCode}.
 * Translated to RFC 7807 ProblemDetail by {@link GlobalExceptionHandler}.
 */
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<String> details;

    public AppException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public AppException(ErrorCode errorCode, String message, List<String> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = List.copyOf(details);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<String> details() {
        return details;
    }
}
