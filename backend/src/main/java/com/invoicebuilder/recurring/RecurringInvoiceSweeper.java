package com.invoicebuilder.recurring;

import com.invoicebuilder.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Daily job that turns due recurring schedules into DRAFT invoices (optionally
 * auto-sent), one tenant at a time so a bad tenant cannot block the rest.
 * Mirrors {@link com.invoicebuilder.invoice.OverdueSweeper}.
 */
@Component
public class RecurringInvoiceSweeper {

    private static final Logger log = LoggerFactory.getLogger(RecurringInvoiceSweeper.class);

    private final RecurringInvoiceRepository recurringRepository;
    private final RecurringInvoiceService recurringService;
    private final Clock clock;

    public RecurringInvoiceSweeper(RecurringInvoiceRepository recurringRepository,
                                   RecurringInvoiceService recurringService,
                                   Clock clock) {
        this.recurringRepository = recurringRepository;
        this.recurringService = recurringService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "UTC")
    public void sweep() {
        LocalDate today = LocalDate.now(clock);
        List<UUID> tenantIds = recurringRepository.findTenantIdsWithDueSchedules(today);
        int total = 0;
        for (UUID tenantId : tenantIds) {
            TenantContext.set(tenantId);
            try {
                total += recurringService.generateDueForTenant(tenantId, today);
            } catch (RuntimeException e) {
                log.error("Recurring sweep failed for tenant {}", tenantId, e);
            } finally {
                TenantContext.clear();
            }
        }
        if (!tenantIds.isEmpty()) {
            log.info("Recurring sweep created {} draft(s) across {} tenant(s)", total, tenantIds.size());
        }
    }
}
