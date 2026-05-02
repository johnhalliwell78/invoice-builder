package com.invoicebuilder.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ProblemDetail> handleAppException(AppException ex, HttpServletRequest request) {
        ErrorCode code = ex.errorCode();
        log.debug("AppException at {}: {} — {}", request.getRequestURI(), code.code(), ex.getMessage());
        return build(code, ex.getMessage(), ex.details(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, "Validation failed", details, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMalformed(HttpMessageNotReadableException ex,
                                                         HttpServletRequest request) {
        return build(ErrorCode.MALFORMED_REQUEST, "Malformed JSON request", List.of(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex,
                                                              HttpServletRequest request) {
        return build(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password", List.of(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex,
                                                              HttpServletRequest request) {
        return build(ErrorCode.AUTHENTICATION_REQUIRED, "Authentication required", List.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest request) {
        return build(ErrorCode.ACCESS_DENIED, "Access denied", List.of(), request);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(Exception ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Resource not found");
        pd.setTitle("Not Found");
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("code", "NOT_FOUND");
        pd.setProperty("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", List.of(), request);
    }

    private ResponseEntity<ProblemDetail> build(ErrorCode code, String message,
                                                List<String> details, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.status(), message);
        pd.setTitle(code.status().getReasonPhrase());
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("code", code.code());
        pd.setProperty("timestamp", Instant.now().toString());
        if (!details.isEmpty()) {
            pd.setProperty("details", details);
        }
        return ResponseEntity.status(code.status()).body(pd);
    }
}
