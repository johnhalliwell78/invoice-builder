package com.invoicebuilder.stripe;

import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.config.AppProperties;
import com.invoicebuilder.payment.PaymentMethod;
import com.invoicebuilder.payment.PaymentService;
import com.invoicebuilder.tenant.TenantContext;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Books Stripe payments from webhook deliveries.
 *
 * <p>The browser redirect after Checkout is cosmetic and forgeable; this
 * webhook is the only thing that moves money in our books. Every delivery is
 * signature-verified, and every booking is idempotent because Stripe retries
 * deliveries and may report one payment through more than one event.</p>
 */
@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    /**
     * {@code completed} covers instant methods (cards);
     * {@code async_payment_succeeded} covers delayed ones (e.g. SEPA debit)
     * whose session completed while the money was still in flight.
     */
    private static final Set<String> HANDLED = Set.of(
            "checkout.session.completed",
            "checkout.session.async_payment_succeeded");

    private final StripeEventRepository stripeEventRepository;
    private final PaymentService paymentService;
    private final AppProperties appProperties;
    private final Clock clock;

    public StripeWebhookService(StripeEventRepository stripeEventRepository,
                                PaymentService paymentService,
                                AppProperties appProperties,
                                Clock clock) {
        this.stripeEventRepository = stripeEventRepository;
        this.paymentService = paymentService;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    /**
     * Verifies and processes one delivery. The event ledger insert and the
     * payment share this transaction: a failure rolls back both, so Stripe's
     * retry finds the event unprocessed and tries again rather than leaving
     * collected money unbooked.
     *
     * @param payload   the raw request body, byte-for-byte as Stripe signed it
     * @param signature the {@code Stripe-Signature} header
     */
    @Transactional
    public void handle(String payload, String signature) {
        String secret = appProperties.stripe() == null ? null : appProperties.stripe().webhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new AppException(ErrorCode.INVOICE_NOT_FOUND, "Stripe webhooks are not configured");
        }

        if (signature == null || signature.isBlank()) {
            // The SDK NPEs on a null header; reject it as the forgery it is.
            log.warn("Rejected Stripe webhook with no signature header");
            throw new AppException(ErrorCode.INVALID_TOKEN, "Missing Stripe signature");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, secret);
        } catch (SignatureVerificationException e) {
            // Never trust an unsigned or mis-signed body — it did not come from Stripe.
            log.warn("Rejected Stripe webhook with invalid signature");
            throw new AppException(ErrorCode.INVALID_TOKEN, "Invalid Stripe signature");
        }

        if (!HANDLED.contains(event.getType())) {
            log.debug("Ignoring unhandled Stripe event type {}", event.getType());
            return;
        }
        if (stripeEventRepository.existsById(event.getId())) {
            log.debug("Stripe event {} already processed", event.getId());
            return;
        }

        Session session = extractSession(event);
        if (session == null) {
            log.warn("Stripe event {} carried no readable checkout session", event.getId());
            return;
        }
        // A completed session is not necessarily a paid one (delayed methods).
        if (!"paid".equals(session.getPaymentStatus())) {
            log.info("Stripe session {} is not paid yet (status {})",
                    session.getId(), session.getPaymentStatus());
            return;
        }

        UUID invoiceId = metadataUuid(session, StripeCheckoutService.METADATA_INVOICE_ID);
        UUID tenantId = metadataUuid(session, StripeCheckoutService.METADATA_TENANT_ID);
        if (invoiceId == null || tenantId == null) {
            log.warn("Stripe session {} is missing invoice/tenant metadata", session.getId());
            return;
        }

        Long amountTotal = session.getAmountTotal();
        if (amountTotal == null || amountTotal <= 0) {
            log.warn("Stripe session {} reported no amount", session.getId());
            return;
        }
        // Book what Stripe actually collected, not the balance we computed when
        // the session was created — a manual payment may have landed since.
        BigDecimal amount = MoneyUnits.fromMinorUnits(amountTotal, session.getCurrency());
        String externalId = session.getPaymentIntent() != null
                ? session.getPaymentIntent()
                : session.getId();

        stripeEventRepository.save(new StripeEvent(
                event.getId(), event.getType(), invoiceId, OffsetDateTime.now(clock)));

        // Webhooks are anonymous: no JWT, so no tenant on the thread. Resolve
        // it from the session metadata the way the sweepers do.
        TenantContext.set(tenantId);
        try {
            paymentService.recordExternal(invoiceId, amount, PaymentMethod.CARD, externalId,
                            "Stripe " + session.getId())
                    .ifPresentOrElse(
                            payment -> log.info("Booked Stripe payment {} for invoice {}",
                                    externalId, invoiceId),
                            () -> log.info("Stripe payment {} was already booked", externalId));
        } finally {
            TenantContext.clear();
        }
    }

    private static Session extractSession(Event event) {
        Optional<StripeObject> deserialized = event.getDataObjectDeserializer().getObject();
        StripeObject object = deserialized.orElseGet(() -> {
            // API-version drift between Stripe and this SDK: fall back to the
            // permissive reader rather than dropping a real payment.
            try {
                return event.getDataObjectDeserializer().deserializeUnsafe();
            } catch (RuntimeException | com.stripe.exception.EventDataObjectDeserializationException e) {
                log.warn("Could not deserialize Stripe event {}", event.getId(), e);
                return null;
            }
        });
        return object instanceof Session session ? session : null;
    }

    private static UUID metadataUuid(Session session, String key) {
        Map<String, String> metadata = session.getMetadata();
        if (metadata == null) {
            return null;
        }
        String raw = metadata.get(key);
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
