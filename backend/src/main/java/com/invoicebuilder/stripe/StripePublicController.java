package com.invoicebuilder.stripe;

import com.invoicebuilder.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Anonymous payment endpoints. Both live under {@code /api/v1/public/**}:
 * recipients are not logged in, and Stripe cannot authenticate to us — the
 * webhook proves its origin with a signature instead.
 */
@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Payments (public)", description = "Anonymous checkout and Stripe webhook")
public class StripePublicController {

    private final StripeCheckoutService checkoutService;
    private final StripeWebhookService webhookService;

    public StripePublicController(StripeCheckoutService checkoutService,
                                  StripeWebhookService webhookService) {
        this.checkoutService = checkoutService;
        this.webhookService = webhookService;
    }

    @PostMapping("/invoices/{token}/checkout")
    @Operation(summary = "Start a Stripe Checkout session for an invoice's remaining balance")
    public ApiResponse<Map<String, String>> checkout(@PathVariable String token) {
        return ApiResponse.of(Map.of("url", checkoutService.createCheckoutUrl(token)));
    }

    /**
     * Takes the body as a raw String on purpose: signature verification runs
     * over the exact bytes Stripe signed, so the payload must not be parsed
     * and re-serialized first.
     */
    @PostMapping("/stripe/webhook")
    @Operation(summary = "Stripe webhook receiver (signature-verified)")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
                                        @RequestHeader(name = "Stripe-Signature", required = false)
                                        String signature) {
        StripeWebhookService.Outcome outcome = webhookService.handle(payload, signature);
        // A 2xx tells Stripe to stop redelivering. Only say that when the
        // payment is genuinely accounted for.
        return outcome == StripeWebhookService.Outcome.RETRY
                ? ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
                : ResponseEntity.ok().build();
    }
}
