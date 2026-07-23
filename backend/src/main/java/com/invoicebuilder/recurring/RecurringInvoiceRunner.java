package com.invoicebuilder.recurring;

import com.invoicebuilder.invoice.DocType;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceService;
import com.invoicebuilder.invoice.dto.InvoiceRequest;
import com.invoicebuilder.invoice.dto.LineItemRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Executes one recurring schedule in its own transaction. Separate bean (not
 * a method on {@link RecurringInvoiceService}) because REQUIRES_NEW only
 * applies across proxy boundaries: with this isolation, one broken schedule
 * can neither mark a shared tenant-wide transaction rollback-only nor take
 * the other schedules' drafts down with it.
 */
@Service
public class RecurringInvoiceRunner {

    private final RecurringInvoiceRepository recurringRepository;
    private final InvoiceService invoiceService;

    public RecurringInvoiceRunner(RecurringInvoiceRepository recurringRepository,
                                  @Lazy InvoiceService invoiceService) {
        this.recurringRepository = recurringRepository;
        this.invoiceService = invoiceService;
    }

    /**
     * Creates the draft for a due schedule and advances {@code nextRun} past
     * {@code today}, atomically. Returns the draft's id, or null when the
     * schedule is gone or no longer due. Auto-send deliberately does NOT
     * happen here — a mail failure must not roll back the created draft.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID runOne(UUID scheduleId, UUID tenantId, LocalDate today) {
        RecurringInvoice schedule = recurringRepository.findByIdAndTenantId(scheduleId, tenantId)
                .orElse(null);
        if (schedule == null || !schedule.isActive() || schedule.getNextRun().isAfter(today)) {
            return null;
        }
        Invoice draft = invoiceService.create(toInvoiceRequest(schedule, today));
        advance(schedule, today);
        return draft.getId();
    }

    /**
     * Advances a schedule past {@code today} without generating anything —
     * the fallback when {@link #runOne} failed, so a permanently broken
     * schedule skips its period instead of retry-storming every sweep.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void advanceOnly(UUID scheduleId, UUID tenantId, LocalDate today) {
        recurringRepository.findByIdAndTenantId(scheduleId, tenantId)
                .ifPresent(schedule -> advance(schedule, today));
    }

    private static void advance(RecurringInvoice schedule, LocalDate today) {
        LocalDate next = schedule.getNextRun();
        while (!next.isAfter(today)) {
            next = RecurringInvoiceService.advance(next, schedule.getFrequency(), schedule.getAnchorDay());
        }
        schedule.setNextRun(next);
    }

    private static InvoiceRequest toInvoiceRequest(RecurringInvoice schedule, LocalDate today) {
        return new InvoiceRequest(
                schedule.getCustomerId(),
                schedule.getCurrency(),
                today,
                today.plusDays(schedule.getDueDays()),
                schedule.getLineItems().stream()
                        .map(li -> new LineItemRequest(li.description(), li.quantity(),
                                li.unitPrice(), li.taxRate(), li.discountPercent()))
                        .toList(),
                schedule.getDiscountAmount(),
                schedule.getNotes(),
                schedule.getTerms(),
                schedule.getTemplate(),
                DocType.INVOICE
        );
    }
}
