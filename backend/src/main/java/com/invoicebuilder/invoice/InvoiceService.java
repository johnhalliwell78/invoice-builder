package com.invoicebuilder.invoice;

import com.invoicebuilder.auth.UserPrincipal;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.invoice.dto.InvoiceRequest;
import com.invoicebuilder.invoice.dto.LineItemRequest;
import com.invoicebuilder.tenant.Tenant;
import com.invoicebuilder.tenant.TenantContext;
import com.invoicebuilder.tenant.TenantRepository;
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
import java.util.List;
import java.util.UUID;

@Service
public class InvoiceService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final TenantRepository tenantRepository;
    private final InvoiceNumberGenerator numberGenerator;
    private final InvoiceCalculator calculator;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          CustomerRepository customerRepository,
                          TenantRepository tenantRepository,
                          InvoiceNumberGenerator numberGenerator,
                          InvoiceCalculator calculator,
                          Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.tenantRepository = tenantRepository;
        this.numberGenerator = numberGenerator;
        this.calculator = calculator;
        this.clock = clock;
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

        Invoice invoice = new Invoice();
        invoice.setTenantId(tenantId);
        invoice.setCustomerId(request.customerId());
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setInvoiceNumber(numberGenerator.reserveNext(tenantId));
        invoice.setCurrency(resolveCurrency(request.currency(), tenantId));
        invoice.setIssueDate(request.issueDate());
        invoice.setDueDate(request.dueDate());
        invoice.setNotes(request.notes());
        invoice.setTerms(request.terms());
        invoice.setTemplate(request.template() == null || request.template().isBlank()
                ? "classic" : request.template().trim());
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

        invoice.setCustomerId(request.customerId());
        invoice.setCurrency(resolveCurrency(request.currency(), invoice.getTenantId()));
        invoice.setIssueDate(request.issueDate());
        invoice.setDueDate(request.dueDate());
        invoice.setNotes(request.notes());
        invoice.setTerms(request.terms());
        if (request.template() != null && !request.template().isBlank()) {
            invoice.setTemplate(request.template().trim());
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
    public Invoice send(UUID id) {
        Invoice invoice = load(id);
        invoice.getStatus().requireTransition(InvoiceStatus.SENT);
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setSentAt(OffsetDateTime.now(clock));
        if (invoice.getPublicToken() == null) {
            invoice.setPublicToken(generatePublicToken());
        }
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

    private void verifyCustomer(UUID customerId, UUID tenantId) {
        customerRepository.findByIdAndTenantIdAndDeletedAtIsNull(customerId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND,
                        "Customer not found in this tenant"));
    }

    private String resolveCurrency(String requested, UUID tenantId) {
        if (requested != null && !requested.isBlank()) {
            return requested.toUpperCase();
        }
        return tenantRepository.findById(tenantId).map(Tenant::getDefaultCurrency).orElse("USD");
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
