package com.invoicebuilder.stripe;

import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.config.AppProperties;
import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.invoice.DocType;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceRepository;
import com.invoicebuilder.invoice.InvoiceStatus;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Creates Stripe Checkout Sessions for invoice recipients. The hosted page
 * means no card data reaches this application.
 *
 * <p>Deliberately <strong>not</strong> {@code @Transactional}: the outbound
 * Stripe call takes hundreds of milliseconds, and holding a pooled database
 * connection across it would let a handful of anonymous callers exhaust the
 * pool and stall the whole application — including the webhook that books
 * money. Each repository call gets its own short transaction instead.</p>
 */
@Service
public class StripeCheckoutService {

    private static final Logger log = LoggerFactory.getLogger(StripeCheckoutService.class);

    private static final Set<InvoiceStatus> PAYABLE =
            EnumSet.of(InvoiceStatus.SENT, InvoiceStatus.VIEWED, InvoiceStatus.OVERDUE);

    static final String METADATA_INVOICE_ID = "invoiceId";
    static final String METADATA_TENANT_ID = "tenantId";

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final StripeCheckoutSessionRepository sessionRepository;
    private final AppProperties appProperties;
    private final StripeClient client;
    private final Clock clock;

    // Explicit: a second (test-only) constructor exists, and Spring cannot
    // pick between them without being told which one to inject through.
    @Autowired
    public StripeCheckoutService(InvoiceRepository invoiceRepository,
                                 CustomerRepository customerRepository,
                                 StripeCheckoutSessionRepository sessionRepository,
                                 AppProperties appProperties,
                                 Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.sessionRepository = sessionRepository;
        this.appProperties = appProperties;
        this.clock = clock;
        // Built once: the client owns connection state, so per-request
        // construction would repeat TLS setup on every checkout.
        this.client = appProperties.stripe() != null && appProperties.stripe().enabled()
                ? new StripeClient(appProperties.stripe().secretKey())
                : null;
    }

    /** Test seam: lets the double-charge guards be exercised without a live account. */
    StripeCheckoutService(InvoiceRepository invoiceRepository,
                          CustomerRepository customerRepository,
                          StripeCheckoutSessionRepository sessionRepository,
                          AppProperties appProperties,
                          Clock clock,
                          StripeClient client) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.sessionRepository = sessionRepository;
        this.appProperties = appProperties;
        this.clock = clock;
        this.client = client;
    }

    /**
     * Online payment needs <em>both</em> Stripe keys — see
     * {@link AppProperties.Stripe#enabled()}.
     */
    public boolean enabled() {
        return client != null;
    }

    /** Whether this invoice could be paid online right now (drives the UI button). */
    public boolean payableNow(Invoice invoice) {
        if (!enabled() || invoice.getDocType() != DocType.INVOICE
                || !PAYABLE.contains(invoice.getStatus())) {
            return false;
        }
        BigDecimal balance = invoice.getTotal().subtract(invoice.getAmountPaid());
        // A balance that cannot be expressed in the currency's minor unit (a
        // fractional JPY amount, say) is unchargeable — hide the button
        // rather than offering one that fails on every click.
        return balance.compareTo(BigDecimal.ZERO) > 0
                && MoneyUnits.isRepresentable(balance, invoice.getCurrency());
    }

    /**
     * Returns a hosted payment URL for the invoice's remaining balance.
     *
     * <p>Reuses a still-open session when one exists: Stripe will not let a
     * single session be paid twice, so reuse is what actually stops a second
     * click from becoming a second charge.</p>
     */
    public String createCheckoutUrl(String publicToken) {
        if (!enabled()) {
            throw new AppException(ErrorCode.INVOICE_NOT_FOUND, "Online payment is not enabled");
        }
        Invoice invoice = invoiceRepository.findByPublicToken(publicToken)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND, "Invoice not found"));
        BigDecimal balance = payableBalance(invoice);

        String currency = invoice.getCurrency().toLowerCase(Locale.ROOT);
        long amount = MoneyUnits.toMinorUnits(balance, invoice.getCurrency());

        String reused = reusableSessionUrl(invoice.getId(), amount, currency);
        return reused != null ? reused : createSession(invoice, amount, currency, publicToken);
    }

    /**
     * Decides whether an existing session can serve this request.
     *
     * <p>Two rules, in this order. First, if <em>any</em> live session for the
     * invoice is already {@code complete}, money is collected or in flight and
     * we must not offer another — checked before amount matching, because a
     * partial payment recorded meanwhile changes the balance and would
     * otherwise hide the completed session. Second, an {@code open} session
     * for the same amount is reused, since Stripe will not charge one session
     * twice.</p>
     *
     * <p>Fails <strong>closed</strong>: if Stripe cannot tell us a session's
     * status we refuse rather than mint a second payable session, because the
     * failure mode of guessing wrong is charging someone twice.</p>
     */
    private String reusableSessionUrl(UUID invoiceId, long amount, String currency) {
        List<StripeCheckoutSession> candidates =
                sessionRepository.findByInvoiceIdAndExpiresAtAfterOrderByCreatedAtDesc(
                        invoiceId, OffsetDateTime.now(clock));
        String openMatch = null;
        for (StripeCheckoutSession candidate : candidates) {
            Session live;
            try {
                live = client.v1().checkout().sessions().retrieve(candidate.getId());
            } catch (StripeException e) {
                log.error("Could not read Stripe session {} — refusing to start another payment",
                        candidate.getId(), e);
                throw new AppException(ErrorCode.INTERNAL_ERROR,
                        "Could not verify the payment status. Please try again shortly.");
            }
            if ("complete".equals(live.getStatus())) {
                throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                        "A payment for this invoice is already being processed");
            }
            if (openMatch == null && "open".equals(live.getStatus())
                    && candidate.getAmountMinor() == amount
                    && candidate.getCurrency().equals(currency)) {
                openMatch = candidate.getUrl();
            }
        }
        return openMatch;
    }

    private String createSession(Invoice invoice, long amount, String currency, String publicToken) {
        String baseUrl = trimTrailingSlash(appProperties.publicBaseUrl());
        String returnUrl = baseUrl + "/i/" + publicToken;

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(returnUrl + "?payment=success")
                .setCancelUrl(returnUrl + "?payment=cancelled")
                .setClientReferenceId(invoice.getId().toString())
                .setCustomerEmail(recipientEmail(invoice))
                .putMetadata(METADATA_INVOICE_ID, invoice.getId().toString())
                .putMetadata(METADATA_TENANT_ID, invoice.getTenantId().toString())
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .putMetadata(METADATA_INVOICE_ID, invoice.getId().toString())
                        .putMetadata(METADATA_TENANT_ID, invoice.getTenantId().toString())
                        .build())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currency)
                                .setUnitAmount(amount)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(invoice.getInvoiceNumber())
                                        .build())
                                .build())
                        .build())
                .build();

        // Deterministic idempotency key: two clicks racing each other resolve
        // to the SAME session inside Stripe, so no local lock is needed to
        // stop a double charge. Valid for 24h, matching session lifetime.
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("checkout:%s:%d:%s".formatted(invoice.getId(), amount, currency))
                .build();
        Session session;
        try {
            session = client.v1().checkout().sessions().create(params, options);
        } catch (StripeException e) {
            log.error("Stripe checkout session creation failed for invoice {}", invoice.getId(), e);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Could not start the payment");
        }

        OffsetDateTime expiresAt = session.getExpiresAt() != null
                ? OffsetDateTime.ofInstant(Instant.ofEpochSecond(session.getExpiresAt()), clock.getZone())
                : OffsetDateTime.now(clock).plusHours(24);
        sessionRepository.save(new StripeCheckoutSession(session.getId(), invoice.getTenantId(),
                invoice.getId(), amount, currency, session.getUrl(), expiresAt, OffsetDateTime.now(clock)));
        return session.getUrl();
    }

    /** Validates the invoice is payable and returns what is still owed. */
    private BigDecimal payableBalance(Invoice invoice) {
        if (invoice.getDocType() != DocType.INVOICE) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION, "Estimates cannot be paid");
        }
        if (!PAYABLE.contains(invoice.getStatus())) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION, "Invoice is not payable");
        }
        BigDecimal balance = invoice.getTotal().subtract(invoice.getAmountPaid());
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION, "Invoice has no remaining balance");
        }
        return balance;
    }

    private String recipientEmail(Invoice invoice) {
        return customerRepository.findById(invoice.getCustomerId())
                .map(Customer::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .orElse(null);
    }

    private static String trimTrailingSlash(String url) {
        String value = url == null ? "" : url;
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
