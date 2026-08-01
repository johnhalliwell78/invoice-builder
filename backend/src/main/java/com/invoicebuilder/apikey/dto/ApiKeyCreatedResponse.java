package com.invoicebuilder.apikey.dto;

/**
 * Creation response. {@code secret} appears here and nowhere else, ever —
 * only its hash is stored, so it cannot be shown again.
 */
public record ApiKeyCreatedResponse(
        ApiKeyResponse key,
        String secret
) {
}
