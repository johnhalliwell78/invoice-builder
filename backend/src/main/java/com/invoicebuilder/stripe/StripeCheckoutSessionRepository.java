package com.invoicebuilder.stripe;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface StripeCheckoutSessionRepository extends JpaRepository<StripeCheckoutSession, String> {

    List<StripeCheckoutSession> findByInvoiceIdAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID invoiceId, OffsetDateTime now, Limit limit);
}
