package com.invoicebuilder.stripe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ledger of Stripe webhook events we have already processed. The Stripe event
 * id is the primary key, so a redelivery collides instead of double-booking.
 */
@Entity
@Table(name = "stripe_event")
public class StripeEvent {

    @Id
    @Column(length = 255)
    private String id;

    @Column(nullable = false, length = 100)
    private String type;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    protected StripeEvent() {
    }

    public StripeEvent(String id, String type, UUID invoiceId, OffsetDateTime receivedAt) {
        this.id = id;
        this.type = type;
        this.invoiceId = invoiceId;
        this.receivedAt = receivedAt;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }
}
