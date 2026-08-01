package com.invoicebuilder.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Signs outgoing deliveries so receivers can prove the request came from us.
 *
 * <p>Deliberately the same scheme Stripe uses on the webhooks we consume —
 * {@code t=<unix>,v1=<hmac-sha256 of "timestamp.payload">} — so anyone
 * integrating can reuse a well-documented verification recipe, and the
 * timestamp lets them reject replays.</p>
 */
public final class WebhookSignature {

    public static final String HEADER = "X-InvoiceBuilder-Signature";

    private WebhookSignature() {
    }

    public static String sign(String payload, String secret, long timestampSeconds) {
        return "t=%d,v1=%s".formatted(timestampSeconds,
                hmacHex(timestampSeconds + "." + payload, secret));
    }

    private static String hmacHex(String signedPayload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
