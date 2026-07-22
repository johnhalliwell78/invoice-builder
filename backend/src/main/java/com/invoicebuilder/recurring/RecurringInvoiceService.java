package com.invoicebuilder.recurring;

import com.invoicebuilder.audit.AuditAction;
import com.invoicebuilder.audit.AuditService;
import com.invoicebuilder.auth.UserPrincipal;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.invoice.DocType;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceRepository;
import com.invoicebuilder.invoice.InvoiceService;
import com.invoicebuilder.invoice.dto.InvoiceRequest;
import com.invoicebuilder.invoice.dto.LineItemRequest;
import com.invoicebuilder.recurring.dto.MakeRecurringRequest;
import com.invoicebuilder.recurring.dto.RecurringInvoiceResponse;
import com.invoicebuilder.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RecurringInvoiceService {

    private static final Logger log = LoggerFactory.getLogger(RecurringInvoiceService.class);

    private final RecurringInvoiceRepository recurringRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceService invoiceService;
    private final AuditService auditService;
    private final Clock clock;

    public RecurringInvoiceService(RecurringInvoiceRepository recurringRepository,
                                   InvoiceRepository invoiceRepository,
                                   CustomerRepository customerRepository,
                                   @Lazy InvoiceService invoiceService,
                                   AuditService auditService,
                                   Clock clock) {
        this.recurringRepository = recurringRepository;
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.invoiceService = invoiceService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Advances a run date by one period. Monthly/yearly runs stay anchored to
     * {@code anchorDay}: a schedule created on the 31st clamps to Feb 28 but
     * returns to the 31st in longer months.
     */
    static LocalDate advance(LocalDate from, Frequency frequency, int anchorDay) {
        return switch (frequency) {
            case DAILY -> from.plusDays(1);
            case WEEKLY -> from.plusWeeks(1);
            case MONTHLY -> withAnchor(from.plusMonths(1), anchorDay);
            case YEARLY -> withAnchor(from.plusYears(1), anchorDay);
        };
    }

    private static LocalDate withAnchor(LocalDate date, int anchorDay) {
        return date.withDayOfMonth(Math.min(anchorDay, date.lengthOfMonth()));
    }

    @Transactional
    public RecurringInvoiceResponse makeRecurring(UUID invoiceId, MakeRecurringRequest request) {
        UUID tenantId = TenantContext.require();
        Invoice source = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND, "Invoice not found"));
        if (source.getDocType() != DocType.INVOICE) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only invoices can be made recurring");
        }

        LocalDate today = LocalDate.now(clock);
        RecurringInvoice schedule = new RecurringInvoice();
        schedule.setTenantId(tenantId);
        schedule.setCustomerId(source.getCustomerId());
        schedule.setFrequency(request.frequency());
        schedule.setAutoSend(request.autoSend());
        schedule.setActive(true);
        schedule.setCurrency(source.getCurrency());
        schedule.setTemplate(source.getTemplate());
        schedule.setDueDays((int) Math.max(0,
                ChronoUnit.DAYS.between(source.getIssueDate(), source.getDueDate())));
        schedule.setDiscountAmount(source.getDiscountAmount());
        schedule.setNotes(source.getNotes());
        schedule.setTerms(source.getTerms());
        schedule.setLineItems(source.getLineItems().stream()
                .map(li -> new RecurringLineItem(li.getDescription(), li.getQuantity(),
                        li.getUnitPrice(), li.getTaxRate(), li.getDiscountPercent()))
                .toList());
        schedule.setCreatedBy(currentUserId());

        LocalDate firstRun = request.firstRun() != null
                ? request.firstRun()
                : advance(today, request.frequency(), today.getDayOfMonth());
        schedule.setAnchorDay(firstRun.getDayOfMonth());
        schedule.setNextRun(firstRun);

        RecurringInvoice saved = recurringRepository.save(schedule);
        auditService.record(tenantId, "RecurringInvoice", saved.getId(), AuditAction.CREATE,
                Map.<String, Object>of("fromInvoice", source.getInvoiceNumber()));
        return RecurringInvoiceResponse.from(saved, resolveName(saved.getCustomerId()));
    }

    @Transactional(readOnly = true)
    public Page<RecurringInvoiceResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.require();
        Page<RecurringInvoice> page = recurringRepository.findByTenantId(tenantId, pageable);
        if (page.isEmpty()) {
            return page.map(r -> RecurringInvoiceResponse.from(r, null));
        }
        Set<UUID> ids = page.getContent().stream()
                .map(RecurringInvoice::getCustomerId)
                .collect(Collectors.toSet());
        Map<UUID, String> names = customerRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName));
        return page.map(r -> RecurringInvoiceResponse.from(r, names.get(r.getCustomerId())));
    }

    @Transactional
    public RecurringInvoiceResponse toggle(UUID id) {
        RecurringInvoice schedule = load(id);
        schedule.setActive(!schedule.isActive());
        auditService.record(schedule.getTenantId(), "RecurringInvoice", schedule.getId(),
                AuditAction.UPDATE, Map.<String, Object>of("active", schedule.isActive()));
        return RecurringInvoiceResponse.from(schedule, resolveName(schedule.getCustomerId()));
    }

    @Transactional
    public void delete(UUID id) {
        RecurringInvoice schedule = load(id);
        auditService.record(schedule.getTenantId(), "RecurringInvoice", schedule.getId(),
                AuditAction.DELETE, null);
        recurringRepository.delete(schedule);
    }

    /**
     * Generates drafts for every due schedule of one tenant. Requires
     * {@link TenantContext} to be set (the sweeper does this per tenant).
     * Each schedule yields at most one draft per sweep; after a long outage
     * the schedule fast-forwards past today instead of spamming drafts.
     * Failures are isolated per schedule and the date still advances so a
     * permanently broken schedule cannot retry-storm forever.
     */
    @Transactional
    public int generateDueForTenant(UUID tenantId, LocalDate today) {
        List<RecurringInvoice> due =
                recurringRepository.findByTenantIdAndActiveTrueAndNextRunLessThanEqual(tenantId, today);
        int created = 0;
        for (RecurringInvoice schedule : due) {
            try {
                Invoice draft = invoiceService.create(toInvoiceRequest(schedule, today));
                if (schedule.isAutoSend()) {
                    invoiceService.send(draft.getId(), null);
                }
                created++;
            } catch (RuntimeException e) {
                log.error("Recurring generation failed for schedule {} (tenant {})",
                        schedule.getId(), tenantId, e);
            }
            LocalDate next = schedule.getNextRun();
            while (!next.isAfter(today)) {
                next = advance(next, schedule.getFrequency(), schedule.getAnchorDay());
            }
            schedule.setNextRun(next);
        }
        return created;
    }

    private InvoiceRequest toInvoiceRequest(RecurringInvoice schedule, LocalDate today) {
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

    private RecurringInvoice load(UUID id) {
        return recurringRepository.findByIdAndTenantId(id, TenantContext.require())
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND,
                        "Recurring schedule not found"));
    }

    private String resolveName(UUID customerId) {
        return customerRepository.findByTenantIdAndIdIn(TenantContext.require(), Set.of(customerId))
                .stream().findFirst().map(Customer::getName).orElse(null);
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal up ? up.userId() : null;
    }
}
