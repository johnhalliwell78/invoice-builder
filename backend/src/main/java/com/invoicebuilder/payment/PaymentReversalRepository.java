package com.invoicebuilder.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentReversalRepository extends JpaRepository<PaymentReversal, UUID> {

    boolean existsByExternalId(String externalId);

    List<PaymentReversal> findByInvoiceIdAndTenantIdOrderByCreatedAtDesc(UUID invoiceId, UUID tenantId);

    /** Batch form for exports — avoids a per-payment query while streaming. */
    List<PaymentReversal> findByTenantIdAndPaymentIdIn(UUID tenantId, java.util.Collection<UUID> paymentIds);

    /** How much of one payment has already been given back. Never null. */
    @Query("select coalesce(sum(r.amount), 0) from PaymentReversal r where r.paymentId = :paymentId")
    BigDecimal sumByPaymentId(@Param("paymentId") UUID paymentId);
}
