package com.invoicebuilder.auth;

import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Issues, validates, and rotates refresh tokens.
 *
 * <p>Tokens are 256-bit secrets sent to the client as opaque strings; only their
 * SHA-256 hashes are persisted, so a database leak does not compromise sessions.</p>
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final AppProperties appProperties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository,
                               AppProperties appProperties,
                               Clock clock) {
        this.repository = repository;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    public record IssuedToken(String rawToken, OffsetDateTime expiresAt) {
    }

    @Transactional
    public IssuedToken issue(UUID userId) {
        String raw = generateRandomToken();
        OffsetDateTime expiresAt = OffsetDateTime.now(clock)
                .plus(appProperties.jwt().refreshTokenExpiry());

        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(expiresAt);
        repository.save(token);

        return new IssuedToken(raw, expiresAt);
    }

    /**
     * Validates the supplied raw token and atomically rotates it: the existing
     * record is revoked and a new one is issued. Returns the new opaque token.
     */
    @Transactional
    public IssuedToken rotate(String rawToken) {
        RefreshToken existing = findActive(rawToken);
        existing.setRevokedAt(OffsetDateTime.now(clock));
        repository.save(existing);
        return issue(existing.getUserId());
    }

    @Transactional(readOnly = true)
    public UUID resolveUserId(String rawToken) {
        return findActive(rawToken).getUserId();
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken))
                .filter(rt -> rt.getRevokedAt() == null)
                .ifPresent(rt -> {
                    rt.setRevokedAt(OffsetDateTime.now(clock));
                    repository.save(rt);
                });
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        repository.revokeAllForUser(userId, OffsetDateTime.now(clock));
    }

    private RefreshToken findActive(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID, "Missing refresh token");
        }
        RefreshToken token = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token not recognized"));
        if (token.isExpiredOrRevoked(OffsetDateTime.now(clock))) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token expired or revoked");
        }
        return token;
    }

    private static String generateRandomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
