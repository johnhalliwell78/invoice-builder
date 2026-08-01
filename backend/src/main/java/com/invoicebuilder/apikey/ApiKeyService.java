package com.invoicebuilder.apikey;

import com.invoicebuilder.apikey.dto.ApiKeyCreatedResponse;
import com.invoicebuilder.apikey.dto.ApiKeyRequest;
import com.invoicebuilder.apikey.dto.ApiKeyResponse;
import com.invoicebuilder.audit.AuditAction;
import com.invoicebuilder.audit.AuditService;
import com.invoicebuilder.auth.UserPrincipal;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApiKeyService {

    /** Recognisable, greppable, and lets us reject non-keys without a query. */
    static final String PREFIX = "ib_";
    private static final int PREFIX_KEPT = 11;
    /** Stamping last-used on every call would make each API request a write. */
    private static final Duration LAST_USED_RESOLUTION = Duration.ofMinutes(5);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository repository;
    private final AuditService auditService;
    private final Clock clock;

    public ApiKeyService(ApiKeyRepository repository, AuditService auditService, Clock clock) {
        this.repository = repository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public ApiKeyCreatedResponse create(ApiKeyRequest request) {
        UUID tenantId = TenantContext.require();
        String secret = generateSecret();

        ApiKey key = new ApiKey();
        key.setTenantId(tenantId);
        key.setName(request.name().trim());
        key.setKeyHash(hash(secret));
        key.setKeyPrefix(secret.substring(0, PREFIX_KEPT));
        key.setRole(request.role());
        key.setCreatedBy(currentUserId());
        ApiKey saved = repository.save(key);

        auditService.record(tenantId, "ApiKey", saved.getId(), AuditAction.CREATE,
                Map.<String, Object>of("name", saved.getName(), "role", saved.getRole().name()));
        // The only moment the plaintext exists outside the caller's request.
        return new ApiKeyCreatedResponse(ApiKeyResponse.from(saved), secret);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list() {
        return repository.findByTenantIdOrderByCreatedAtDesc(TenantContext.require())
                .stream().map(ApiKeyResponse::from).toList();
    }

    @Transactional
    public void revoke(UUID id) {
        UUID tenantId = TenantContext.require();
        ApiKey key = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "API key not found"));
        if (key.isRevoked()) {
            return;
        }
        key.setRevokedAt(OffsetDateTime.now(clock));
        auditService.record(tenantId, "ApiKey", key.getId(), AuditAction.DELETE,
                Map.<String, Object>of("name", key.getName()));
    }

    /**
     * Resolves a presented secret to its key, or empty if it is unknown,
     * malformed, or revoked.
     *
     * <p>Runs before any tenant is known — the key is what establishes it.</p>
     */
    @Transactional
    public Optional<ApiKey> authenticate(String secret) {
        if (secret == null || !secret.startsWith(PREFIX)) {
            // Not one of ours; don't spend a query on it.
            return Optional.empty();
        }
        Optional<ApiKey> found = repository.findByKeyHash(hash(secret))
                .filter(key -> !key.isRevoked());
        found.ifPresent(this::touch);
        return found;
    }

    /** Coarse last-used stamp: useful for spotting dead keys, cheap enough to keep. */
    private void touch(ApiKey key) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (key.getLastUsedAt() == null
                || Duration.between(key.getLastUsedAt(), now).compareTo(LAST_USED_RESOLUTION) >= 0) {
            key.setLastUsedAt(now);
        }
    }

    private static String generateSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal up ? up.userId() : null;
    }
}
