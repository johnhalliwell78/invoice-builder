package com.invoicebuilder.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByInvoiceIdAndTenantIdOrderByPaidOnDescCreatedAtDesc(UUID invoiceId, UUID tenantId);

    boolean existsByExternalId(String externalId);

    java.util.Optional<Payment> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Resolves a Stripe PaymentIntent back to the receipt we booked for it. */
    java.util.Optional<Payment> findByExternalIdAndTenantId(String externalId, UUID tenantId);

    /**
     * Tenant-less lookup used only by webhooks: a refund or dispute arrives
     * with no tenant context, and a Charge does not reliably carry the
     * metadata we attached to the Checkout Session, so our own payment row is
     * the authority on which tenant the money belongs to.
     */
    java.util.Optional<Payment> findByExternalId(String externalId);
}
