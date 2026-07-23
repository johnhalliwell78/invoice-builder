package com.invoicebuilder.invoice;

import com.invoicebuilder.audit.AuditService;
import com.invoicebuilder.common.exception.AppException;
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
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceEstimateTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ESTIMATE_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-22T10:00:00Z");

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
    private Invoice estimate;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoiceRepository, customerRepository, tenantRepository,
                numberGenerator, new InvoiceCalculator(), pdfGenerator, pdfStorage, logoStorage,
                emailService, messages, reminderRepository, auditService, eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        TenantContext.set(TENANT_ID);

        estimate = new Invoice();
        estimate.setTenantId(TENANT_ID);
        estimate.setCustomerId(CUSTOMER_ID);
        estimate.setDocType(DocType.ESTIMATE);
        estimate.setInvoiceNumber("EST-2026-0003");
        estimate.setStatus(InvoiceStatus.SENT);
        estimate.setCurrency("EUR");
        estimate.setTemplate("classic");
        estimate.setNotes("Scope as discussed");
        estimate.setTerms("Valid 30 days");
        // 10-day term: issue 2026-07-01, due 2026-07-11.
        estimate.setIssueDate(LocalDate.of(2026, 7, 1));
        estimate.setDueDate(LocalDate.of(2026, 7, 11));

        InvoiceLineItem item = new InvoiceLineItem();
        item.setDescription("Discovery workshop");
        item.setQuantity(new BigDecimal("2"));
        item.setUnitPrice(new BigDecimal("500.00"));
        item.setTaxRate(new BigDecimal("19.00"));
        item.setDiscountPercent(BigDecimal.ZERO);
        item.setSortOrder(0);
        estimate.addLineItem(item);

        lenient().when(invoiceRepository.findByIdAndTenantId(ESTIMATE_ID, TENANT_ID))
                .thenReturn(Optional.of(estimate));
        lenient().when(invoiceRepository.findByIdAndTenantIdForUpdate(ESTIMATE_ID, TENANT_ID))
                .thenReturn(Optional.of(estimate));
        lenient().when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void approveTransitionsOpenEstimate() {
        Invoice approved = service.approve(ESTIMATE_ID);

        assertThat(approved.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
    }

    @Test
    void declineTransitionsViewedEstimate() {
        estimate.setStatus(InvoiceStatus.VIEWED);

        Invoice declined = service.decline(ESTIMATE_ID);

        assertThat(declined.getStatus()).isEqualTo(InvoiceStatus.DECLINED);
    }

    @Test
    void approveRejectsPlainInvoices() {
        estimate.setDocType(DocType.INVOICE);

        assertThatThrownBy(() -> service.approve(ESTIMATE_ID)).isInstanceOf(AppException.class);
    }

    @Test
    void convertCreatesLinkedInvoiceDraftWithFreshNumberAndShiftedDates() {
        estimate.setStatus(InvoiceStatus.APPROVED);
        when(numberGenerator.reserveNext(TENANT_ID)).thenReturn("INV-2026-0055");

        Invoice invoice = service.convert(ESTIMATE_ID);

        assertThat(invoice.getDocType()).isEqualTo(DocType.INVOICE);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(invoice.getInvoiceNumber()).isEqualTo("INV-2026-0055");
        assertThat(invoice.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(invoice.getCurrency()).isEqualTo("EUR");
        assertThat(invoice.getNotes()).isEqualTo("Scope as discussed");
        assertThat(invoice.getLineItems()).hasSize(1);
        // 2 × 500 = 1000 + 19% tax = 1190
        assertThat(invoice.getTotal()).isEqualByComparingTo("1190.00");
        // Term preserved, shifted to fixed today 2026-07-22 (+10 days).
        assertThat(invoice.getIssueDate()).isEqualTo(LocalDate.of(2026, 7, 22));
        assertThat(invoice.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        // Source estimate is stamped with the link.
        assertThat(estimate.getConvertedInvoiceId()).isEqualTo(invoice.getId());
    }

    @Test
    void convertRequiresApprovedEstimate() {
        assertThatThrownBy(() -> service.convert(ESTIMATE_ID)).isInstanceOf(AppException.class);
    }

    @Test
    void convertRejectsSecondConversion() {
        estimate.setStatus(InvoiceStatus.APPROVED);
        estimate.setConvertedInvoiceId(UUID.randomUUID());

        assertThatThrownBy(() -> service.convert(ESTIMATE_ID)).isInstanceOf(AppException.class);
    }

    @Test
    void duplicateKeepsDocTypeAndNumberSeries() {
        when(numberGenerator.reserveNextEstimate(TENANT_ID)).thenReturn("EST-2026-0009");

        Invoice copy = service.duplicate(ESTIMATE_ID);

        assertThat(copy.getDocType()).isEqualTo(DocType.ESTIMATE);
        assertThat(copy.getInvoiceNumber()).isEqualTo("EST-2026-0009");
        assertThat(copy.getConvertedInvoiceId()).isNull();
    }
}
