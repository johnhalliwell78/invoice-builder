package com.invoicebuilder.apikey.dto;

import com.invoicebuilder.apikey.ApiKey;
import com.invoicebuilder.user.Role;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A key as listed afterwards — never carries the secret. */
public record ApiKeyResponse(
        UUID id,
        String name,
        String keyPrefix,
        Role role,
        OffsetDateTime lastUsedAt,
        OffsetDateTime revokedAt,
        OffsetDateTime createdAt
) {

    public static ApiKeyResponse from(ApiKey key) {
        return new ApiKeyResponse(key.getId(), key.getName(), key.getKeyPrefix(), key.getRole(),
                key.getLastUsedAt(), key.getRevokedAt(), key.getCreatedAt());
    }
}
