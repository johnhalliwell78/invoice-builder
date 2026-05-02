package com.invoicebuilder.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Root configuration properties bound from {@code app.*} in application.yml.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull Jwt jwt,
        OAuth2 oauth2,
        Sendgrid sendgrid,
        @NotNull Storage storage,
        @NotNull Cors cors,
        @NotNull RateLimit rateLimit
) {

    public record Jwt(
            @NotBlank String secret,
            @NotNull Duration accessTokenExpiry,
            @NotNull Duration refreshTokenExpiry,
            @NotBlank String issuer
    ) {
    }

    public record OAuth2(
            String successRedirectUri,
            String failureRedirectUri
    ) {
    }

    public record Sendgrid(
            String apiKey,
            String fromEmail,
            String fromName
    ) {
    }

    public record Storage(
            @NotNull Path pdfPath,
            @NotNull Path logoPath
    ) {
    }

    public record Cors(
            @NotNull List<String> allowedOrigins
    ) {
    }

    public record RateLimit(
            int loginAttempts,
            @NotNull Duration loginWindow,
            int apiRequestsPerMinute
    ) {
    }
}
