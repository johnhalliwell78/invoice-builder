package com.invoicebuilder.invoice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Nightly job that flips SENT/VIEWED invoices past their due date to OVERDUE
 * and triggers reminder emails, one tenant at a time so a bad tenant cannot
 * block the rest.
 */
@Component
public class OverdueSweeper {

    private static final Logger log = LoggerFactory.getLogger(OverdueSweeper.class);
    private static final List<InvoiceStatus> CANDIDATE_STATUSES =
            List.of(InvoiceStatus.SENT, InvoiceStatus.VIEWED);

    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final Clock clock;

    public OverdueSweeper(InvoiceRepository invoiceRepository,
                          InvoiceService invoiceService,
                          Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
    public void sweep() {
        LocalDate today = LocalDate.now(clock);
        List<UUID> tenantIds = invoiceRepository.findTenantIdsWithOverdueCandidates(CANDIDATE_STATUSES, today);
        int total = 0;
        for (UUID tenantId : tenantIds) {
            try {
                total += invoiceService.markOverdueForTenant(tenantId, today);
            } catch (RuntimeException e) {
                log.error("Overdue sweep failed for tenant {}", tenantId, e);
            }
        }
        if (!tenantIds.isEmpty()) {
            log.info("Overdue sweep marked {} invoice(s) across {} tenant(s)", total, tenantIds.size());
        }
    }
}
