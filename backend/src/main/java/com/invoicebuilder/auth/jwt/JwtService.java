package com.invoicebuilder.auth.jwt;

import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.config.AppProperties;
import com.invoicebuilder.user.AppUser;
import com.invoicebuilder.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and parses HS256 JWT access tokens.
 *
 * <p>Access tokens are short-lived (default 15m). The signing key is derived
 * from {@code app.jwt.secret} and must be at least 32 bytes for HMAC-SHA256.</p>
 */
@Service
public class JwtService {

    public static final String CLAIM_TENANT_ID = "tid";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_EMAIL = "email";

    private final AppProperties appProperties;
    private final Clock clock;
    private final SecretKey signingKey;

    public JwtService(AppProperties appProperties, Clock clock) {
        this.appProperties = appProperties;
        this.clock = clock;
        byte[] secretBytes = appProperties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes; got " + secretBytes.length);
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateAccessToken(AppUser user) {
        Instant now = clock.instant();
        Instant expiry = now.plus(appProperties.jwt().accessTokenExpiry());
        return Jwts.builder()
                .subject(user.getId().toString())
                .issuer(appProperties.jwt().issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim(CLAIM_TENANT_ID, user.getTenantId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_EMAIL, user.getEmail())
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public ParsedToken parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(appProperties.jwt().issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new ParsedToken(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get(CLAIM_TENANT_ID, String.class)),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class)),
                    claims.get(CLAIM_EMAIL, String.class)
            );
        } catch (ExpiredJwtException e) {
            throw new AppException(ErrorCode.TOKEN_EXPIRED, "Access token expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_TOKEN, "Invalid access token");
        }
    }

    public record ParsedToken(UUID userId, UUID tenantId, Role role, String email) {
    }
}
