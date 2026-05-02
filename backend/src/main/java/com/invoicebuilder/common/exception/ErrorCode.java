package com.invoicebuilder.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // ---- 400 Bad Request ----
    VALIDATION_FAILED("VALIDATION_FAILED", HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST("MALFORMED_REQUEST", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD_FORMAT("INVALID_PASSWORD_FORMAT", HttpStatus.BAD_REQUEST),

    // ---- 401 Unauthorized ----
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN("INVALID_TOKEN", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID("REFRESH_TOKEN_INVALID", HttpStatus.UNAUTHORIZED),
    AUTHENTICATION_REQUIRED("AUTHENTICATION_REQUIRED", HttpStatus.UNAUTHORIZED),

    // ---- 403 Forbidden ----
    ACCESS_DENIED("ACCESS_DENIED", HttpStatus.FORBIDDEN),
    USER_INACTIVE("USER_INACTIVE", HttpStatus.FORBIDDEN),

    // ---- 404 Not Found ----
    USER_NOT_FOUND("USER_NOT_FOUND", HttpStatus.NOT_FOUND),
    TENANT_NOT_FOUND("TENANT_NOT_FOUND", HttpStatus.NOT_FOUND),
    INVOICE_NOT_FOUND("INVOICE_NOT_FOUND", HttpStatus.NOT_FOUND),
    CUSTOMER_NOT_FOUND("CUSTOMER_NOT_FOUND", HttpStatus.NOT_FOUND),

    // ---- 409 Conflict ----
    EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT),
    INVOICE_NOT_EDITABLE("INVOICE_NOT_EDITABLE", HttpStatus.CONFLICT),
    INVALID_STATE_TRANSITION("INVALID_STATE_TRANSITION", HttpStatus.CONFLICT),

    // ---- 429 Too Many Requests ----
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS),

    // ---- 500 Internal Server Error ----
    INTERNAL_ERROR("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final HttpStatus status;

    ErrorCode(String code, HttpStatus status) {
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
