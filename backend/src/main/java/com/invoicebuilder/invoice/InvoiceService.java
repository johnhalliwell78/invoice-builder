package com.invoicebuilder.invoice;

import com.invoicebuilder.audit.AuditAction;
import com.invoicebuilder.audit.AuditService;
import com.invoicebuilder.auth.UserPrincipal;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.email.EmailService;
import com.invoicebuilder.invoice.dto.InvoiceListItem;
import com.invoicebuilder.invoice.dto.InvoiceRequest;
import com.invoicebuilder.invoice.dto.LineItemRequest;
import com.invoicebuilder.invoice.dto.SendInvoiceRequest;
import com.invoicebuilder.notification.NotificationEvent;
import com.invoicebuilder.notification.NotificationType;
import com.invoicebuilder.pdf.InvoicePdfGenerator;
import com.invoicebuilder.pdf.PdfStorage;
import com.invoicebuilder.tenant.LogoStorage;
import com.invoicebuilder.tenant.Tenant;
import com.invoicebuilder.tenant.TenantContext;
import com.invoicebuilder.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final TenantRepository tenantRepository;
    private final InvoiceNumberGenerator numberGenerator;
    private final InvoiceCalculator calculator;
    private final InvoicePdfGenerator pdfGenerator;
    private final PdfStorage pdfStorage;
    private final LogoStorage logoStorage;
    private final EmailService emailService;
    private final MessageSource messages;
    private final InvoiceReminderRepository reminderRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          CustomerRepository customerRepository,
                          TenantRepository tenantRepository,
                          InvoiceNumberGenerator numberGenerator,
                          InvoiceCalculator calculator,
                          InvoicePdfGenerator pdfGenerator,
                          PdfStorage pdfStorage,
                          LogoStorage logoStorage,
                          EmailService emailService,
                          MessageSource messages,
                          InvoiceReminderRepository reminderRepository,
                          AuditService auditService,
                          ApplicationEventPublisher eventPublisher,
                          Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.tenantRepository = tenantRepository;
        this.numberGenerator = numberGenerator;
        this.calculator = calculator;
        this.pdfGenerator = pdfGenerator;
        this.pdfStorage = pdfStorage;
        this.logoStorage = logoStorage;
        this.emailService = emailService;
        this.messages = messages;
        this.reminderRepository = reminderRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Renders the invoice PDF and persists it to storage. Always regenerates
     * since DRAFT invoices may have changed; storage doubles as an artifact
     * cache for downstream features (audit trail, email attachment).
     */
    @Transactional(readOnly = true)
    public byte[] renderPdf(UUID id) {
        return renderPdf(id, null);
    }

    /**
     * Renders with an optional template override (transient — the stored
     * template and the cached artifact are only written for canonical renders).
     */
    @Transactional(readOnly = true)
    public byte[] renderPdf(UUID id, String templateOverride) {
        Invoice invoice = load(id);
        String override = templateOverride == null || templateOverride.isBlank()
                ? null : requireKnownTemplate(templateOverride.trim());
        Tenant tenant = loadTenant(invoice.getTenantId());
        Customer customer = loadCustomer(invoice.getCustomerId());
        byte[] pdf = pdfGenerator.render(invoice, tenant, customer, override,
                logoStorage.loadOrNull(tenant));
        if (override == null) {
            pdfStorage.save(invoice.getTenantId(), invoice.getId(), pdf);
        }
        return pdf;
    }

    public String suggestedFilename(UUID id) {
        return invoiceRepository.findByIdAndTenantId(id, TenantContext.require())
                .map(i -> filenameFor(i))
                .orElse("invoice.pdf");
    }

    /** List projection with customer names resolved in a single batch query. */
    @Transactional(readOnly = true)
    public Page<InvoiceListItem> listItems(DocType docType,
                                           InvoiceStatus status,
                                           UUID customerId,
                                           LocalDate from,
                                           LocalDate to,
                                           Pageable pageable) {
        UUID tenantId = TenantContext.require();
        Page<Invoice> page = invoiceRepository.search(
                tenantId, docType == null ? DocType.INVOICE : docType, status, customerId, from, to, pageable);
        if (page.isEmpty()) {
            return page.map(i -> InvoiceListItem.from(i, null));
        }
        Set<UUID> customerIds = page.getContent().stream()
                .map(Invoice::getCustomerId)
                .collect(Collectors.toSet());
        Map<UUID, String> names = customerRepository.findByTenantIdAndIdIn(tenantId, customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName));
        return page.map(i -> InvoiceListItem.from(i, names.get(i.getCustomerId())));
    }

    @Transactional(readOnly = true)
    public Invoice get(UUID id) {
        return load(id);
    }

    @Transactional
    public Invoice create(InvoiceRequest request) {
        UUID tenantId = TenantContext.require();
        verifyCustomer(request.customerId(), tenantId);
        Tenant tenant = loadTenant(tenantId);

        Invoice invoice = new Invoice();
        invoice.setTenantId(tenantId);
        invoice.setCustomerId(request.customerId());
        invoice.setStatus(InvoiceStatus.DRAFT);
        DocType docType = request.docType() == null ? DocType.INVOICE : request.docType();
        invoice.setDocType(docType);
        invoice.setInvoiceNumber(docType == DocType.ESTIMATE
                ? numberGenerator.reserveNextEstimate(tenantId)
                : numberGenerator.reserveNext(tenantId));
        invoice.setCurrency(resolveCurrency(request.currency(), tenant));
        invoice.setIssueDate(request.issueDate());
        invoice.setDueDate(request.dueDate());
        invoice.setNotes(request.notes());
        invoice.setTerms(request.terms());
        invoice.setTemplate(resolveTemplate(request.template(), tenant));
        invoice.setCreatedBy(currentUserId());

        applyLineItems(invoice, request.lineItems());
        applyTotals(invoice, request.discountAmount());

        Invoice saved = invoiceRepository.save(invoice);
        auditInvoice(saved, AuditAction.CREATE, null);
        return saved;
    }

    @Transactional
    public Invoice update(UUID id, InvoiceRequest request) {
        Invoice invoice = load(id);
        if (!invoice.getStatus().isEditable()) {
            throw new AppException(ErrorCode.INVOICE_NOT_EDITABLE,
                    "Only DRAFT invoices can be edited");
        }
        verifyCustomer(request.customerId(), invoice.getTenantId());
        Tenant tenant = loadTenant(invoice.getTenantId());

        invoice.setCustomerId(request.customerId());
        invoice.setCurrency(resolveCurrency(request.currency(), tenant));
        invoice.setIssueDate(request.issueDate());
        invoice.setDueDate(request.dueDate());
        invoice.setNotes(request.notes());
        invoice.setTerms(request.terms());
        if (request.template() != null && !request.template().isBlank()) {
            invoice.setTemplate(requireKnownTemplate(request.template().trim()));
        }

        invoice.clearLineItems();
        applyLineItems(invoice, request.lineItems());
        applyTotals(invoice, request.discountAmount());
        auditInvoice(invoice, AuditAction.UPDATE, null);
        return invoice;
    }

    /**
     * Creates a fresh DRAFT copy of any invoice: same customer, currency,
     * template, notes, terms, and line items; new number; dates shifted to
     * today with the source's payment term preserved. Lifecycle state
     * (token, sent/viewed/paid, payments) is never carried over.
     */
    @Transactional
    public Invoice duplicate(UUID id) {
        Invoice source = load(id);
        LocalDate today = LocalDate.now(clock);
        long termDays = java.time.temporal.ChronoUnit.DAYS.between(
                source.getIssueDate(), source.getDueDate());

        Invoice copy = new Invoice();
        copy.setTenantId(source.getTenantId());
        copy.setCustomerId(source.getCustomerId());
        copy.setStatus(InvoiceStatus.DRAFT);
        copy.setDocType(source.getDocType());
        copy.setInvoiceNumber(source.getDocType() == DocType.ESTIMATE
                ? numberGenerator.reserveNextEstimate(source.getTenantId())
                : numberGenerator.reserveNext(source.getTenantId()));
        copy.setCurrency(source.getCurrency());
        copy.setIssueDate(today);
        copy.setDueDate(today.plusDays(Math.max(0, termDays)));
        copy.setNotes(source.getNotes());
        copy.setTerms(source.getTerms());
        copy.setTemplate(source.getTemplate());
        copy.setCreatedBy(currentUserId());
        for (InvoiceLineItem item : source.getLineItems()) {
            InvoiceLineItem line = new InvoiceLineItem();
            line.setDescription(item.getDescription());
            line.setQuantity(item.getQuantity());
            line.setUnitPrice(item.getUnitPrice());
            line.setTaxRate(item.getTaxRate());
            line.setDiscountPercent(item.getDiscountPercent());
            line.setSortOrder(item.getSortOrder());
            copy.addLineItem(line);
        }
        applyTotals(copy, source.getDiscountAmount());

        Invoice saved = invoiceRepository.save(copy);
        auditInvoice(saved, AuditAction.CREATE,
                Map.<String, Object>of("duplicatedFrom", source.getInvoiceNumber()));
        return saved;
    }

    @Transactional
    public Invoice approve(UUID id) {
        Invoice estimate = loadEstimate(id);
        estimate.getStatus().requireTransition(DocType.ESTIMATE, InvoiceStatus.APPROVED);
        estimate.setStatus(InvoiceStatus.APPROVED);
        auditInvoice(estimate, AuditAction.STATUS_CHANGE, Map.<String, Object>of("status", "APPROVED"));
        return estimate;
    }

    @Transactional
    public Invoice decline(UUID id) {
        Invoice estimate = loadEstimate(id);
        estimate.getStatus().requireTransition(DocType.ESTIMATE, InvoiceStatus.DECLINED);
        estimate.setStatus(InvoiceStatus.DECLINED);
        auditInvoice(estimate, AuditAction.STATUS_CHANGE, Map.<String, Object>of("status", "DECLINED"));
        return estimate;
    }

    /**
     * Converts an APPROVED estimate into a fresh DRAFT invoice (copy like
     * {@link #duplicate}, but always doc type INVOICE with an INV number) and
     * stamps the estimate with the created invoice's id. One-shot: a second
     * conversion is rejected.
     */
    @Transactional
    public Invoice convert(UUID id) {
        Invoice estimate = loadEstimate(id);
        if (estimate.getStatus() != InvoiceStatus.APPROVED) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only APPROVED estimates can be converted");
        }
        if (estimate.getConvertedInvoiceId() != null) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Estimate was already converted");
        }

        LocalDate today = LocalDate.now(clock);
        long termDays = java.time.temporal.ChronoUnit.DAYS.between(
                estimate.getIssueDate(), estimate.getDueDate());

        Invoice invoice = new Invoice();
        invoice.setTenantId(estimate.getTenantId());
        invoice.setCustomerId(estimate.getCustomerId());
        invoice.setDocType(DocType.INVOICE);
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setInvoiceNumber(numberGenerator.reserveNext(estimate.getTenantId()));
        invoice.setCurrency(estimate.getCurrency());
        invoice.setIssueDate(today);
        invoice.setDueDate(today.plusDays(Math.max(0, termDays)));
        invoice.setNotes(estimate.getNotes());
        invoice.setTerms(estimate.getTerms());
        invoice.setTemplate(estimate.getTemplate());
        invoice.setCreatedBy(currentUserId());
        for (InvoiceLineItem item : estimate.getLineItems()) {
            InvoiceLineItem line = new InvoiceLineItem();
            line.setDescription(item.getDescription());
            line.setQuantity(item.getQuantity());
            line.setUnitPrice(item.getUnitPrice());
            line.setTaxRate(item.getTaxRate());
            line.setDiscountPercent(item.getDiscountPercent());
            line.setSortOrder(item.getSortOrder());
            invoice.addLineItem(line);
        }
        applyTotals(invoice, estimate.getDiscountAmount());

        Invoice saved = invoiceRepository.save(invoice);
        estimate.setConvertedInvoiceId(saved.getId());
        auditInvoice(saved, AuditAction.CREATE,
                Map.<String, Object>of("convertedFrom", estimate.getInvoiceNumber()));
        auditInvoice(estimate, AuditAction.UPDATE,
                Map.<String, Object>of("convertedTo", saved.getInvoiceNumber()));
        return saved;
    }

    private Invoice loadEstimate(UUID id) {
        // Row lock: two concurrent convert/approve calls serialize, so the
        // one-shot convertedInvoiceId guard and transitions cannot race.
        Invoice invoice = invoiceRepository.findByIdAndTenantIdForUpdate(id, TenantContext.require())
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND, "Invoice not found"));
        if (invoice.getDocType() != DocType.ESTIMATE) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Action only applies to estimates");
        }
        return invoice;
    }

    @Transactional
    public void delete(UUID id) {
        Invoice invoice = load(id);
        if (!invoice.getStatus().isEditable()) {
            throw new AppException(ErrorCode.INVOICE_NOT_EDITABLE,
                    "Only DRAFT invoices can be deleted");
        }
        auditInvoice(invoice, AuditAction.DELETE, null);
        invoiceRepository.delete(invoice);
    }

    @Transactional
    public Invoice send(UUID id, SendInvoiceRequest request) {
        Invoice invoice = load(id);
        invoice.getStatus().requireTransition(invoice.getDocType(), InvoiceStatus.SENT);
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setSentAt(OffsetDateTime.now(clock));
        if (invoice.getPublicToken() == null) {
            invoice.setPublicToken(generatePublicToken());
        }

        if (request == null || !request.isEmailSkipped()) {
            sendInvoiceEmail(invoice, request);
        }
        auditInvoice(invoice, AuditAction.SEND, Map.<String, Object>of("status", "SENT"));
        notifyInvoice(invoice, NotificationType.INVOICE_SENT);
        return invoice;
    }

    /** Resolved email content — what will actually be sent for this invoice. */
    public record EmailPreview(String recipientEmail, String subject, String body) {
    }

    @Transactional(readOnly = true)
    public EmailPreview previewEmail(UUID id) {
        Invoice invoice = load(id);
        Tenant tenant = loadTenant(invoice.getTenantId());
        Customer customer = loadCustomer(invoice.getCustomerId());
        return composeEmail(invoice, tenant, customer, null);
    }

    /**
     * Resolves recipient, subject, and body: explicit request values win,
     * otherwise localized defaults from the tenant's locale bundle.
     */
    private static String filenameFor(Invoice invoice) {
        String prefix = invoice.getDocType() == DocType.ESTIMATE ? "estimate-" : "invoice-";
        return prefix + invoice.getInvoiceNumber() + ".pdf";
    }

    private EmailPreview composeEmail(Invoice invoice, Tenant tenant, Customer customer,
                                      SendInvoiceRequest request) {
        String recipient = request != null && request.recipientEmail() != null && !request.recipientEmail().isBlank()
                ? request.recipientEmail()
                : customer.getEmail();

        Locale locale = Locale.forLanguageTag(
                tenant.getDefaultLocale() == null ? "en" : tenant.getDefaultLocale());
        String keyPrefix = invoice.getDocType() == DocType.ESTIMATE ? "email.estimate" : "email.invoice";
        String subject = request != null && request.subject() != null && !request.subject().isBlank()
                ? request.subject()
                : messages.getMessage(keyPrefix + ".subject",
                        new Object[]{invoice.getInvoiceNumber(), tenant.getName()},
                        locale);
        String body = request != null && request.message() != null && !request.message().isBlank()
                ? request.message()
                : messages.getMessage(keyPrefix + ".bodyDefault",
                        new Object[]{invoice.getInvoiceNumber(), invoice.getDueDate(), tenant.getName()},
                        locale);
        return new EmailPreview(recipient, subject, body);
    }

    /**
     * Composes and delivers the invoice email. Returns the recipient the email
     * went to, or {@code null} when delivery was skipped (no address known).
     */
    private String sendInvoiceEmail(Invoice invoice, SendInvoiceRequest request) {
        Tenant tenant = loadTenant(invoice.getTenantId());
        Customer customer = loadCustomer(invoice.getCustomerId());
        EmailPreview content = composeEmail(invoice, tenant, customer, request);
        if (content.recipientEmail() == null || content.recipientEmail().isBlank()) {
            // Customer has no email and caller didn't override — silently skip.
            return null;
        }

        deliver(invoice, tenant, customer, content.recipientEmail(),
                content.subject(), content.body(),
                request == null ? List.of() : request.ccOrEmpty(),
                request == null ? List.of() : request.bccOrEmpty());
        return content.recipientEmail();
    }

    /** Renders the PDF, caches it, and hands the message to the email service. */
    private void deliver(Invoice invoice, Tenant tenant, Customer customer,
                         String recipient, String subject, String body,
                         List<String> cc, List<String> bcc) {
        byte[] pdf = pdfGenerator.render(invoice, tenant, customer, null,
                logoStorage.loadOrNull(tenant));
        pdfStorage.save(invoice.getTenantId(), invoice.getId(), pdf);
        emailService.send(new EmailService.EmailMessage(
                recipient, customer.getName(), cc, bcc, subject, body,
                filenameFor(invoice), pdf));
    }

    private void recordReminder(Invoice invoice, String recipient, ReminderType type) {
        InvoiceReminder reminder = new InvoiceReminder();
        reminder.setTenantId(invoice.getTenantId());
        reminder.setInvoiceId(invoice.getId());
        reminder.setRecipient(recipient);
        reminder.setType(type);
        reminder.setSentAt(OffsetDateTime.now(clock));
        reminderRepository.save(reminder);
    }

    private static final Set<InvoiceStatus> RESENDABLE =
            EnumSet.of(InvoiceStatus.SENT, InvoiceStatus.VIEWED, InvoiceStatus.OVERDUE);

    /** Re-delivers the invoice email without touching status, sentAt, or the public token. */
    @Transactional
    public Invoice resend(UUID id, SendInvoiceRequest request) {
        Invoice invoice = load(id);
        if (!RESENDABLE.contains(invoice.getStatus())) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only sent, viewed, or overdue invoices can be resent");
        }
        String recipient = sendInvoiceEmail(invoice, request);
        if (recipient != null) {
            recordReminder(invoice, recipient, ReminderType.MANUAL_RESEND);
        }
        return invoice;
    }

    @Transactional(readOnly = true)
    public List<InvoiceReminder> listReminders(UUID id) {
        Invoice invoice = load(id);
        return reminderRepository.findByInvoiceIdAndTenantIdOrderBySentAtDesc(
                invoice.getId(), invoice.getTenantId());
    }

    @Transactional
    public Invoice cancel(UUID id) {
        Invoice invoice = load(id);
        invoice.getStatus().requireTransition(invoice.getDocType(), InvoiceStatus.CANCELLED);
        invoice.setStatus(InvoiceStatus.CANCELLED);
        auditInvoice(invoice, AuditAction.STATUS_CHANGE, Map.<String, Object>of("status", "CANCELLED"));
        return invoice;
    }

    private void auditInvoice(Invoice invoice, AuditAction action, Map<String, Object> changes) {
        auditService.record(invoice.getTenantId(), "Invoice", invoice.getId(), action, changes);
    }

    private void notifyInvoice(Invoice invoice, NotificationType type) {
        eventPublisher.publishEvent(new NotificationEvent(invoice.getTenantId(), invoice.getCreatedBy(),
                type, "Invoice", invoice.getId(), invoice.getInvoiceNumber()));
    }

    /**
     * Sweeps invoices past their due date into OVERDUE and emails the customer
     * a localized reminder. Runs from {@link OverdueSweeper} without a request
     * context, so everything is keyed off the explicit {@code tenantId}. A
     * failed reminder email never rolls back the status transition.
     */
    @Transactional
    public int markOverdueForTenant(UUID tenantId, LocalDate today) {
        List<UUID> ids = invoiceRepository.findOverdueIds(
                tenantId, List.of(InvoiceStatus.SENT, InvoiceStatus.VIEWED), today);
        for (UUID id : ids) {
            Invoice invoice = invoiceRepository.findById(id).orElseThrow();
            invoice.setStatus(InvoiceStatus.OVERDUE);
            notifyInvoice(invoice, NotificationType.INVOICE_OVERDUE);
            try {
                sendOverdueReminder(invoice);
            } catch (RuntimeException e) {
                log.warn("Failed to send overdue reminder for invoice {}", invoice.getId(), e);
            }
        }
        return ids.size();
    }

    private void sendOverdueReminder(Invoice invoice) {
        Tenant tenant = loadTenant(invoice.getTenantId());
        Customer customer = loadCustomer(invoice.getCustomerId());
        String recipient = customer.getEmail();
        if (recipient == null || recipient.isBlank()) {
            return;
        }
        Locale locale = Locale.forLanguageTag(
                tenant.getDefaultLocale() == null ? "en" : tenant.getDefaultLocale());
        String subject = messages.getMessage("email.reminder.subject",
                new Object[]{invoice.getInvoiceNumber(), tenant.getName()}, locale);
        String body = messages.getMessage("email.reminder.body",
                new Object[]{invoice.getInvoiceNumber(), invoice.getDueDate(), tenant.getName()}, locale);
        deliver(invoice, tenant, customer, recipient, subject, body, List.of(), List.of());
        recordReminder(invoice, recipient, ReminderType.AUTO_OVERDUE);
    }

    // ---------- helpers ----------

    private Invoice load(UUID id) {
        return invoiceRepository.findByIdAndTenantId(id, TenantContext.require())
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND, "Invoice not found"));
    }

    private Tenant loadTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND, "Tenant not found"));
    }

    private Customer loadCustomer(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND, "Customer not found"));
    }

    private void verifyCustomer(UUID customerId, UUID tenantId) {
        customerRepository.findByIdAndTenantIdAndDeletedAtIsNull(customerId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND,
                        "Customer not found in this tenant"));
    }

    private static String resolveCurrency(String requested, Tenant tenant) {
        if (requested != null && !requested.isBlank()) {
            return requested.toUpperCase();
        }
        return tenant.getDefaultCurrency() == null ? "USD" : tenant.getDefaultCurrency();
    }

    /** Valid explicit template wins; blank falls back to the tenant default. */
    private static String resolveTemplate(String requested, Tenant tenant) {
        if (requested == null || requested.isBlank()) {
            String def = tenant.getDefaultTemplate();
            return def == null || def.isBlank() ? "classic" : def;
        }
        return requireKnownTemplate(requested.trim());
    }

    private static String requireKnownTemplate(String template) {
        if (!InvoicePdfGenerator.TEMPLATES.contains(template)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Unknown template: " + template);
        }
        return template;
    }

    private void applyLineItems(Invoice invoice, List<LineItemRequest> requests) {
        int sort = 0;
        for (LineItemRequest req : requests) {
            InvoiceLineItem item = new InvoiceLineItem();
            item.setDescription(req.description().trim());
            item.setQuantity(req.quantity());
            item.setUnitPrice(req.unitPrice());
            item.setTaxRate(orZero(req.taxRate()));
            item.setDiscountPercent(orZero(req.discountPercent()));
            item.setSortOrder(sort++);
            invoice.addLineItem(item);
        }
    }

    private void applyTotals(Invoice invoice, BigDecimal discountAmount) {
        InvoiceCalculator.Totals totals = calculator.recompute(invoice, discountAmount);
        invoice.setSubtotal(totals.subtotal());
        invoice.setTaxTotal(totals.taxTotal());
        invoice.setDiscountAmount(totals.discountAmount());
        invoice.setTotal(totals.total());
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static UUID currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal instanceof UserPrincipal up ? up.userId() : null;
    }

    private static String generatePublicToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
