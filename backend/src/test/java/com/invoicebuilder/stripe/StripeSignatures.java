package com.invoicebuilder.stripe;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Builds real {@code Stripe-Signature} headers so tests exercise the actual
 * verification path instead of mocking it away.
 */
final class StripeSignatures {

    private StripeSignatures() {
    }

    static String header(String payload, String secret) {
        long timestamp = Instant.now().getEpochSecond();
        return "t=%d,v1=%s".formatted(timestamp, sign(timestamp + "." + payload, secret));
    }

    private static String sign(String signedPayload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
