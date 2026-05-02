package com.invoicebuilder.auth;

import com.invoicebuilder.auth.dto.AuthResponse;
import com.invoicebuilder.auth.dto.LoginRequest;
import com.invoicebuilder.auth.dto.RegisterRequest;
import com.invoicebuilder.auth.dto.UserResponse;
import com.invoicebuilder.common.dto.ApiResponse;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.config.AppProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Register, login, refresh, logout, current user")
public class AuthController {

    static final String REFRESH_COOKIE_NAME = "ib_refresh";
    static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final AppProperties appProperties;

    public AuthController(AuthService authService, AppProperties appProperties) {
        this.authService = authService;
        this.appProperties = appProperties;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new tenant and owner user")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request,
                                                              HttpServletResponse response) {
        AuthService.AuthResult result = authService.register(request);
        addRefreshCookie(response, result.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(result.authResponse()));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email and password")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletResponse response) {
        AuthService.AuthResult result = authService.login(request);
        addRefreshCookie(response, result.refreshToken());
        return ResponseEntity.ok(ApiResponse.of(result.authResponse()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and issue a new access token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(HttpServletRequest request,
                                                             HttpServletResponse response) {
        String refreshToken = extractRefreshCookie(request)
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_INVALID, "Missing refresh cookie"));
        AuthService.AuthResult result = authService.refresh(refreshToken);
        addRefreshCookie(response, result.refreshToken());
        return ResponseEntity.ok(ApiResponse.of(result.authResponse()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the refresh token and clear the cookie")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        extractRefreshCookie(request).ifPresent(authService::logout);
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Return the authenticated user's profile")
    public ApiResponse<UserResponse> currentUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new AppException(ErrorCode.AUTHENTICATION_REQUIRED, "Authentication required");
        }
        return ApiResponse.of(authService.currentUser(principal.userId()));
    }

    // ---------- cookie helpers ----------

    private void addRefreshCookie(HttpServletResponse response, String value) {
        long maxAge = appProperties.jwt().refreshTokenExpiry().toSeconds();
        String cookie = "%s=%s; Max-Age=%d; Path=%s; HttpOnly; Secure; SameSite=Lax".formatted(
                REFRESH_COOKIE_NAME, value, maxAge, REFRESH_COOKIE_PATH);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        String cookie = "%s=; Max-Age=0; Path=%s; HttpOnly; Secure; SameSite=Lax".formatted(
                REFRESH_COOKIE_NAME, REFRESH_COOKIE_PATH);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie);
    }

    private static java.util.Optional<String> extractRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return java.util.Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> REFRESH_COOKIE_NAME.equals(c.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .filter(v -> !v.isBlank());
    }
}
