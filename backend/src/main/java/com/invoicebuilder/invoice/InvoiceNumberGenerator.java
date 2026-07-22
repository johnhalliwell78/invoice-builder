package com.invoicebuilder.invoice;

import com.invoicebuilder.tenant.Tenant;
import com.invoicebuilder.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Atomically reserves the next per-tenant invoice number.
 *
 * <p>Uses a native PostgreSQL {@code UPDATE ... RETURNING} so concurrent
 * callers can never observe the same counter value, even under contention.</p>
 */
@Service
public class InvoiceNumberGenerator {

    private final TenantRepository tenantRepository;

    @PersistenceContext
    private EntityManager em;

    public InvoiceNumberGenerator(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /**
     * Reserves the next number for {@code tenantId} and returns it as a
     * formatted invoice number like {@code INV-2026-0001}.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String reserveNext(UUID tenantId) {
        Number reserved = (Number) em.createNativeQuery(
                        "UPDATE tenant SET next_invoice_number = next_invoice_number + 1 "
                                + "WHERE id = :id RETURNING next_invoice_number - 1")
                .setParameter("id", tenantId)
                .getSingleResult();
        em.flush();

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        em.refresh(tenant);

        return formatNumber(tenant.getInvoicePrefix(), reserved.intValue());
    }

    /**
     * Reserves the next estimate number for {@code tenantId} from its own
     * counter, formatted like {@code EST-2026-0001}. Estimates always use the
     * fixed {@code EST} prefix so they are never confused with invoices.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String reserveNextEstimate(UUID tenantId) {
        Number reserved = (Number) em.createNativeQuery(
                        "UPDATE tenant SET next_estimate_number = next_estimate_number + 1 "
                                + "WHERE id = :id RETURNING next_estimate_number - 1")
                .setParameter("id", tenantId)
                .getSingleResult();
        em.flush();

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        em.refresh(tenant);

        return formatNumber("EST", reserved.intValue());
    }

    static String formatNumber(String prefix, int counter) {
        return "%s-%d-%04d".formatted(prefix == null || prefix.isBlank() ? "INV" : prefix,
                LocalDate.now().getYear(), counter);
    }
}
