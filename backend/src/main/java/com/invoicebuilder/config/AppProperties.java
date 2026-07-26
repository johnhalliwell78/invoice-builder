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
        Stripe stripe,
        String publicBaseUrl,
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

    /**
     * Stripe Checkout. With no {@code secretKey} the whole payment feature is
     * off: endpoints 404 and the UI hides the pay button, so dev/CI need no
     * Stripe account.
     */
    public record Stripe(
            String secretKey,
            String webhookSecret
    ) {

        /**
         * Both keys are required. A secret key alone would charge cards while
         * every webhook that books them is rejected — money collected and
         * never recorded.
         */
        public boolean enabled() {
            return secretKey != null && !secretKey.isBlank()
                    && webhookSecret != null && !webhookSecret.isBlank();
        }
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
            int apiRequestsPerMinute,
            /** Per-IP ceiling for anonymous /api/v1/public/** traffic. */
            int publicRequestsPerMinute
    ) {
    }
}
