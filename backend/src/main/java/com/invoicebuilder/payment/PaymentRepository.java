package com.invoicebuilder.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByInvoiceIdAndTenantIdOrderByPaidOnDescCreatedAtDesc(UUID invoiceId, UUID tenantId);
}
