package com.invoicebuilder.invoice;

import com.invoicebuilder.audit.AuditService;
import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.email.EmailService;
import com.invoicebuilder.pdf.InvoicePdfGenerator;
import com.invoicebuilder.pdf.PdfStorage;
import com.invoicebuilder.tenant.LogoStorage;
import com.invoicebuilder.tenant.TenantContext;
import com.invoicebuilder.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceDuplicateTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    // Fixed "today": 2026-07-21
    private static final Instant NOW = Instant.parse("2026-07-21T10:00:00Z");

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private InvoiceNumberGenerator numberGenerator;
    @Mock private InvoicePdfGenerator pdfGenerator;
    @Mock private PdfStorage pdfStorage;
    @Mock private LogoStorage logoStorage;
    @Mock private EmailService emailService;
    @Mock private MessageSource messages;
    @Mock private InvoiceReminderRepository reminderRepository;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private InvoiceService service;
    private Invoice source;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoiceRepository, customerRepository, tenantRepository,
                numberGenerator, new InvoiceCalculator(), pdfGenerator, pdfStorage, logoStorage,
                emailService, messages, reminderRepository, auditService, eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        TenantContext.set(TENANT_ID);

        source = new Invoice();
        source.setTenantId(TENANT_ID);
        source.setCustomerId(CUSTOMER_ID);
        source.setInvoiceNumber("INV-2026-0001");
        source.setStatus(InvoiceStatus.PAID);
        source.setCurrency("EUR");
        source.setTemplate("modern");
        source.setNotes("Original notes");
        source.setTerms("Net 14");
        // 14-day payment term: issue 2026-06-01, due 2026-06-15.
        source.setIssueDate(LocalDate.of(2026, 6, 1));
        source.setDueDate(LocalDate.of(2026, 6, 15));
        source.setPublicToken("orig-token");
        source.setSentAt(OffsetDateTime.parse("2026-06-01T09:00:00Z"));
        source.setPaidAt(OffsetDateTime.parse("2026-06-10T09:00:00Z"));
        source.setAmountPaid(new BigDecimal("119.00"));
        source.setDiscountAmount(new BigDecimal("5.00"));

        InvoiceLineItem item = new InvoiceLineItem();
        item.setDescription("Consulting");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setTaxRate(new BigDecimal("19.00"));
        item.setDiscountPercent(BigDecimal.ZERO);
        item.setSortOrder(0);
        source.addLineItem(item);

        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
                .thenReturn(Optional.of(source));
        when(numberGenerator.reserveNext(TENANT_ID)).thenReturn("INV-2026-0042");
        lenient().when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void duplicateCopiesContentWithFreshNumberAndDraftStatus() {
        Invoice copy = service.duplicate(INVOICE_ID);

        assertThat(copy).isNotSameAs(source);
        assertThat(copy.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(copy.getInvoiceNumber()).isEqualTo("INV-2026-0042");
        assertThat(copy.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(copy.getCurrency()).isEqualTo("EUR");
        assertThat(copy.getTemplate()).isEqualTo("modern");
        assertThat(copy.getNotes()).isEqualTo("Original notes");
        assertThat(copy.getTerms()).isEqualTo("Net 14");
        assertThat(copy.getLineItems()).hasSize(1);
        assertThat(copy.getLineItems().get(0).getDescription()).isEqualTo("Consulting");
        assertThat(copy.getLineItems().get(0).getUnitPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void duplicateShiftsDatesToTodayPreservingPaymentTerm() {
        Invoice copy = service.duplicate(INVOICE_ID);

        assertThat(copy.getIssueDate()).isEqualTo(LocalDate.of(2026, 7, 21));
        assertThat(copy.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 4)); // +14 days
    }

    @Test
    void duplicateDoesNotCarryLifecycleStateOrPayments() {
        Invoice copy = service.duplicate(INVOICE_ID);

        assertThat(copy.getPublicToken()).isNull();
        assertThat(copy.getSentAt()).isNull();
        assertThat(copy.getPaidAt()).isNull();
        assertThat(copy.getViewedAt()).isNull();
        assertThat(copy.getAmountPaid()).isEqualByComparingTo("0");
        // Totals recomputed from line items + copied invoice discount:
        // 100.00 + 19.00 tax − 5.00 discount = 114.00
        assertThat(copy.getTotal()).isEqualByComparingTo("114.00");
    }
}
