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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
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

    /**
     * What the caller should tell Stripe. {@link #RETRY} must map to a 5xx:
     * Stripe stops redelivering after any 2xx, so acknowledging a payment we
     * failed to book would strand collected money with no ledger row.
     */
    public enum Outcome {
        /** Payment booked. */
        PROCESSED,
        /** Already booked, or an event type we do not act on. */
        ACKNOWLEDGED,
        /** Real payment we could not book — ask Stripe to deliver it again. */
        RETRY
    }

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
    public Outcome handle(String payload, String signature) {
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
            return Outcome.ACKNOWLEDGED;
        }
        if (stripeEventRepository.existsById(event.getId())) {
            log.debug("Stripe event {} already processed", event.getId());
            return Outcome.ACKNOWLEDGED;
        }

        Session session = extractSession(event);
        if (session == null) {
            // Cannot tell whether money moved — make Stripe try again and
            // surface it, instead of silently swallowing a real payment.
            log.error("Stripe event {} ({}) carried no readable checkout session — asking for redelivery",
                    event.getId(), event.getType());
            return Outcome.RETRY;
        }
        // A completed session is not necessarily a paid one (delayed methods).
        if (!"paid".equals(session.getPaymentStatus())) {
            log.info("Stripe session {} is not paid yet (status {})",
                    session.getId(), session.getPaymentStatus());
            return Outcome.ACKNOWLEDGED;
        }

        UUID invoiceId = metadataUuid(session, StripeCheckoutService.METADATA_INVOICE_ID);
        UUID tenantId = metadataUuid(session, StripeCheckoutService.METADATA_TENANT_ID);
        Long amountTotal = session.getAmountTotal();
        if (invoiceId == null || tenantId == null || amountTotal == null || amountTotal <= 0) {
            // Money was collected but we cannot attribute it. Never ACK this.
            log.error("PAID Stripe session {} is unbookable (invoiceId={}, tenantId={}, amount={}) "
                            + "— manual reconciliation required", session.getId(), invoiceId, tenantId, amountTotal);
            return Outcome.RETRY;
        }

        // Book what Stripe actually collected, not the balance we computed when
        // the session was created — a manual payment may have landed since.
        String currency = session.getCurrency() == null
                ? null : session.getCurrency().toUpperCase(Locale.ROOT);
        BigDecimal amount = MoneyUnits.fromMinorUnits(amountTotal, currency);
        String externalId = session.getPaymentIntent() != null
                ? session.getPaymentIntent()
                : session.getId();

        try {
            stripeEventRepository.saveAndFlush(new StripeEvent(
                    event.getId(), event.getType(), invoiceId, OffsetDateTime.now(clock)));
        } catch (DataIntegrityViolationException e) {
            // Concurrent delivery of the same event won the race; the payment
            // it booked is authoritative.
            log.debug("Stripe event {} was processed concurrently", event.getId());
            return Outcome.ACKNOWLEDGED;
        }

        // Webhooks are anonymous: no JWT, so no tenant on the thread. Resolve
        // it from the session metadata the way the sweepers do.
        TenantContext.set(tenantId);
        try {
            Optional<?> booked = paymentService.recordExternal(
                    invoiceId, amount, currency, PaymentMethod.CARD, externalId,
                    "Stripe " + session.getId());
            if (booked.isPresent()) {
                log.info("Booked Stripe payment {} for invoice {}", externalId, invoiceId);
                return Outcome.PROCESSED;
            }
            log.info("Stripe payment {} was already booked", externalId);
            return Outcome.ACKNOWLEDGED;
        } finally {
            TenantContext.clear();
        }
    }

    private static Session extractSession(Event event) {
        Optional<StripeObject> deserialized = event.getDataObjectDeserializer().getObject();
        StripeObject object = deserialized.orElseGet(() -> {
            // Stripe pins each endpoint to the account's API version, so this
            // fallback is the NORMAL path whenever that differs from the SDK's
            // pinned version — not an exotic one.
            try {
                return event.getDataObjectDeserializer().deserializeUnsafe();
            } catch (RuntimeException | com.stripe.exception.EventDataObjectDeserializationException e) {
                log.error("Could not deserialize Stripe event {}", event.getId(), e);
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
