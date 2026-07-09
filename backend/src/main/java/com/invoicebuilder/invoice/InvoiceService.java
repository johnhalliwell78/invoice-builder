package com.invoicebuilder.invoice;

import com.invoicebuilder.auth.UserPrincipal;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.email.EmailService;
import com.invoicebuilder.invoice.dto.InvoiceRequest;
import com.invoicebuilder.invoice.dto.LineItemRequest;
import com.invoicebuilder.invoice.dto.SendInvoiceRequest;
import com.invoicebuilder.pdf.InvoicePdfGenerator;
import com.invoicebuilder.pdf.PdfStorage;
import com.invoicebuilder.tenant.Tenant;
import com.invoicebuilder.tenant.TenantContext;
import com.invoicebuilder.tenant.TenantRepository;
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
import java.util.Set;
import java.util.UUID;

@Service
public class InvoiceService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final TenantRepository tenantRepository;
    private final InvoiceNumberGenerator numberGenerator;
    private final InvoiceCalculator calculator;
    private final InvoicePdfGenerator pdfGenerator;
    private final PdfStorage pdfStorage;
    private final EmailService emailService;
    private final MessageSource messages;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          CustomerRepository customerRepository,
                          TenantRepository tenantRepository,
                          InvoiceNumberGenerator numberGenerator,
                          InvoiceCalculator calculator,
                          InvoicePdfGenerator pdfGenerator,
                          PdfStorage pdfStorage,
                          EmailService emailService,
                          MessageSource messages,
                          Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.tenantRepository = tenantRepository;
        this.numberGenerator = numberGenerator;
        this.calculator = calculator;
        this.pdfGenerator = pdfGenerator;
        this.pdfStorage = pdfStorage;
        this.emailService = emailService;
        this.messages = messages;
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
        byte[] pdf = pdfGenerator.render(invoice, tenant, customer, override);
        if (override == null) {
            pdfStorage.save(invoice.getTenantId(), invoice.getId(), pdf);
        }
        return pdf;
    }

    public String suggestedFilename(UUID id) {
        return invoiceRepository.findByIdAndTenantId(id, TenantContext.require())
                .map(i -> "invoice-" + i.getInvoiceNumber() + ".pdf")
                .orElse("invoice.pdf");
    }

    @Transactional(readOnly = true)
    public Page<Invoice> list(InvoiceStatus status,
                              UUID customerId,
                              LocalDate from,
                              LocalDate to,
                              Pageable pageable) {
        return invoiceRepository.search(TenantContext.require(), status, customerId, from, to, pageable);
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
        invoice.setInvoiceNumber(numberGenerator.reserveNext(tenantId));
        invoice.setCurrency(resolveCurrency(request.currency(), tenant));
        invoice.setIssueDate(request.issueDate());
        invoice.setDueDate(request.dueDate());
        invoice.setNotes(request.notes());
        invoice.setTerms(request.terms());
        invoice.setTemplate(resolveTemplate(request.template(), tenant));
        invoice.setCreatedBy(currentUserId());

        applyLineItems(invoice, request.lineItems());
        applyTotals(invoice, request.discountAmount());

        return invoiceRepository.save(invoice);
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
        return invoice;
    }

    @Transactional
    public void delete(UUID id) {
        Invoice invoice = load(id);
        if (!invoice.getStatus().isEditable()) {
            throw new AppException(ErrorCode.INVOICE_NOT_EDITABLE,
                    "Only DRAFT invoices can be deleted");
        }
        invoiceRepository.delete(invoice);
    }

    @Transactional
    public Invoice send(UUID id, SendInvoiceRequest request) {
        Invoice invoice = load(id);
        invoice.getStatus().requireTransition(InvoiceStatus.SENT);
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setSentAt(OffsetDateTime.now(clock));
        if (invoice.getPublicToken() == null) {
            invoice.setPublicToken(generatePublicToken());
        }

        if (request == null || !request.isEmailSkipped()) {
            sendInvoiceEmail(invoice, request);
        }
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
    private EmailPreview composeEmail(Invoice invoice, Tenant tenant, Customer customer,
                                      SendInvoiceRequest request) {
        String recipient = request != null && request.recipientEmail() != null && !request.recipientEmail().isBlank()
                ? request.recipientEmail()
                : customer.getEmail();

        Locale locale = Locale.forLanguageTag(
                tenant.getDefaultLocale() == null ? "en" : tenant.getDefaultLocale());
        String subject = request != null && request.subject() != null && !request.subject().isBlank()
                ? request.subject()
                : messages.getMessage("email.invoice.subject",
                        new Object[]{invoice.getInvoiceNumber(), tenant.getName()},
                        locale);
        String body = request != null && request.message() != null && !request.message().isBlank()
                ? request.message()
                : messages.getMessage("email.invoice.bodyDefault",
                        new Object[]{invoice.getInvoiceNumber(), invoice.getDueDate(), tenant.getName()},
                        locale);
        return new EmailPreview(recipient, subject, body);
    }

    private void sendInvoiceEmail(Invoice invoice, SendInvoiceRequest request) {
        Tenant tenant = loadTenant(invoice.getTenantId());
        Customer customer = loadCustomer(invoice.getCustomerId());
        EmailPreview content = composeEmail(invoice, tenant, customer, request);
        if (content.recipientEmail() == null || content.recipientEmail().isBlank()) {
            // Customer has no email and caller didn't override — silently skip.
            return;
        }

        byte[] pdf = pdfGenerator.render(invoice, tenant, customer);
        pdfStorage.save(invoice.getTenantId(), invoice.getId(), pdf);

        emailService.send(new EmailService.EmailMessage(
                content.recipientEmail(), customer.getName(),
                request == null ? List.of() : request.ccOrEmpty(),
                request == null ? List.of() : request.bccOrEmpty(),
                content.subject(), content.body(),
                "invoice-" + invoice.getInvoiceNumber() + ".pdf", pdf));
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
        sendInvoiceEmail(invoice, request);
        return invoice;
    }

    @Transactional
    public Invoice markPaid(UUID id) {
        Invoice invoice = load(id);
        invoice.getStatus().requireTransition(InvoiceStatus.PAID);
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(OffsetDateTime.now(clock));
        invoice.setAmountPaid(invoice.getTotal());
        return invoice;
    }

    @Transactional
    public Invoice cancel(UUID id) {
        Invoice invoice = load(id);
        invoice.getStatus().requireTransition(InvoiceStatus.CANCELLED);
        invoice.setStatus(InvoiceStatus.CANCELLED);
        return invoice;
    }

    /** Sweeps invoices past their due date into OVERDUE. Wired to a scheduled job in Phase 6. */
    @Transactional
    public int markOverdueForTenant(UUID tenantId, LocalDate today) {
        List<UUID> ids = invoiceRepository.findOverdueIds(
                tenantId, List.of(InvoiceStatus.SENT, InvoiceStatus.VIEWED), today);
        for (UUID id : ids) {
            Invoice invoice = invoiceRepository.findById(id).orElseThrow();
            invoice.setStatus(InvoiceStatus.OVERDUE);
        }
        return ids.size();
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
