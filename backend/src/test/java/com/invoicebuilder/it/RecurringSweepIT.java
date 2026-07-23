package com.invoicebuilder.it;

import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.invoice.DocType;
import com.invoicebuilder.recurring.Frequency;
import com.invoicebuilder.recurring.RecurringInvoice;
import com.invoicebuilder.recurring.RecurringInvoiceRepository;
import com.invoicebuilder.recurring.RecurringInvoiceService;
import com.invoicebuilder.recurring.RecurringLineItem;
import com.invoicebuilder.tenant.Tenant;
import com.invoicebuilder.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for the sweep transaction-poisoning bug: with real transaction
 * proxies, one schedule whose customer was soft-deleted must NOT roll back
 * the other schedules' drafts or freeze nextRun. (The pre-fix code failed
 * this: the inner create() marked the shared per-tenant transaction
 * rollback-only, everything rolled back, and the sweep repeated forever.)
 */
class RecurringSweepIT extends IntegrationTestBase {

    @Autowired private RecurringInvoiceRepository recurringRepository;
    @Autowired private RecurringInvoiceService recurringService;

    private RecurringInvoice schedule(Tenant tenant, Customer customer, LocalDate nextRun) {
        RecurringInvoice r = new RecurringInvoice();
        r.setTenantId(tenant.getId());
        r.setCustomerId(customer.getId());
        r.setFrequency(Frequency.MONTHLY);
        r.setAnchorDay(nextRun.getDayOfMonth());
        r.setNextRun(nextRun);
        r.setActive(true);
        r.setAutoSend(false);
        r.setCurrency("EUR");
        r.setTemplate("classic");
        r.setDueDays(14);
        r.setDiscountAmount(BigDecimal.ZERO);
        r.setLineItems(List.of(new RecurringLineItem("Retainer", BigDecimal.ONE,
                new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO)));
        return recurringRepository.save(r);
    }

    @Test
    void brokenScheduleDoesNotPoisonTheTenantSweep() {
        Tenant tenant = createTenant();
        Customer healthy = createCustomer(tenant, "Healthy Co");
        Customer vanished = createCustomer(tenant, "Vanished Co");
        softDelete(vanished);

        LocalDate today = LocalDate.now();
        RecurringInvoice healthySchedule = schedule(tenant, healthy, today);
        RecurringInvoice brokenSchedule = schedule(tenant, vanished, today);

        TenantContext.set(tenant.getId());
        int created = recurringService.generateDueForTenant(tenant.getId(), today);
        TenantContext.clear();

        // The healthy schedule's draft survived and is committed.
        assertThat(created).isEqualTo(1);
        assertThat(invoiceRepository.search(tenant.getId(), DocType.INVOICE, null, null, null, null,
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements()).isEqualTo(1);

        // BOTH schedules advanced — the broken one skips its period instead of
        // retry-storming tomorrow.
        assertThat(recurringRepository.findById(healthySchedule.getId()).orElseThrow().getNextRun())
                .isAfter(today);
        assertThat(recurringRepository.findById(brokenSchedule.getId()).orElseThrow().getNextRun())
                .isAfter(today);
    }
}
