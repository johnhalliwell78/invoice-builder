package com.invoicebuilder.stripe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A Checkout Session we handed to a recipient, kept so we can reuse it. */
@Entity
@Table(name = "stripe_checkout_session")
public class StripeCheckoutSession {

    @Id
    @Column(length = 255)
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected StripeCheckoutSession() {
    }

    public StripeCheckoutSession(String id, UUID tenantId, UUID invoiceId, long amountMinor,
                                 String currency, String url, OffsetDateTime expiresAt,
                                 OffsetDateTime createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.invoiceId = invoiceId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.url = url;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public String getUrl() {
        return url;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
