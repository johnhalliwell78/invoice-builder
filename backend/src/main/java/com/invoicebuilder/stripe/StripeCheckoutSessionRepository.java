package com.invoicebuilder.stripe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface StripeCheckoutSessionRepository extends JpaRepository<StripeCheckoutSession, String> {

    /**
     * All live sessions for an invoice. Deliberately uncapped: a completed
     * session hidden behind newer rows would let a second charge through.
     */
    List<StripeCheckoutSession> findByInvoiceIdAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID invoiceId, OffsetDateTime now);
}
