package com.invoicebuilder.invoice;

import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.audit.AuditService;
import org.springframework.context.ApplicationEventPublisher;
import com.invoicebuilder.email.EmailService;
import com.invoicebuilder.invoice.dto.InvoiceRequest;
import com.invoicebuilder.invoice.dto.LineItemRequest;
import com.invoicebuilder.pdf.InvoicePdfGenerator;
import com.invoicebuilder.pdf.PdfStorage;
import com.invoicebuilder.tenant.LogoStorage;
import com.invoicebuilder.tenant.Tenant;
import com.invoicebuilder.tenant.TenantContext;
import com.invoicebuilder.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTemplateTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

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
    private Tenant tenant;
    private Customer customer;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoiceRepository, customerRepository, tenantRepository,
                numberGenerator, new InvoiceCalculator(), pdfGenerator, pdfStorage, logoStorage,
                emailService, messages, reminderRepository, auditService, eventPublisher,
                Clock.fixed(Instant.parse("2026-07-09T12:00:00Z"), ZoneOffset.UTC));
        TenantContext.set(TENANT_ID);

        tenant = new Tenant();
        tenant.setName("Acme GmbH");
        tenant.setDefaultCurrency("EUR");
        tenant.setDefaultTemplate("modern");

        customer = new Customer();
        customer.setTenantId(TENANT_ID);
        customer.setName("Widget Co");

        invoice = new Invoice();
        invoice.setTenantId(TENANT_ID);
        invoice.setCustomerId(CUSTOMER_ID);
        invoice.setInvoiceNumber("INV-2026-0001");
        invoice.setTemplate("classic");

        lenient().when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        lenient().when(customerRepository.findByIdAndTenantIdAndDeletedAtIsNull(CUSTOMER_ID, TENANT_ID))
                .thenReturn(Optional.of(customer));
        lenient().when(numberGenerator.reserveNext(TENANT_ID)).thenReturn("INV-2026-0002");
        lenient().when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private InvoiceRequest request(String template) {
        return new InvoiceRequest(
                CUSTOMER_ID, "EUR",
                LocalDate.of(2026, 7, 9), LocalDate.of(2026, 8, 8),
                List.of(new LineItemRequest("Consulting", BigDecimal.ONE, new BigDecimal("100.00"),
                        BigDecimal.ZERO, BigDecimal.ZERO)),
                BigDecimal.ZERO, null, null, template, null);
    }

    @Test
    void createFallsBackToTenantDefaultTemplate() {
        Invoice created = service.create(request(null));
        assertThat(created.getTemplate()).isEqualTo("modern");
    }

    @Test
    void createUsesExplicitTemplateWhenValid() {
        Invoice created = service.create(request("classic"));
        assertThat(created.getTemplate()).isEqualTo("classic");
    }

    @Test
    void createRejectsUnknownTemplate() {
        assertThatThrownBy(() -> service.create(request("fancy")))
                .isInstanceOf(AppException.class);
    }

    @Test
    void renderPdfHonorsTemplateOverrideWithoutMutatingInvoice() {
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID)).thenReturn(Optional.of(invoice));
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(pdfGenerator.render(eq(invoice), eq(tenant), eq(customer), eq("modern"), nullable(byte[].class)))
                .thenReturn(new byte[]{1});

        byte[] pdf = service.renderPdf(INVOICE_ID, "modern");

        assertThat(pdf).isNotEmpty();
        assertThat(invoice.getTemplate()).isEqualTo("classic");
        verify(pdfGenerator).render(eq(invoice), eq(tenant), eq(customer), eq("modern"), nullable(byte[].class));
    }

    @Test
    void renderPdfWithoutOverrideUsesStoredTemplate() {
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID)).thenReturn(Optional.of(invoice));
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(pdfGenerator.render(eq(invoice), eq(tenant), eq(customer), isNull(), nullable(byte[].class)))
                .thenReturn(new byte[]{1});

        service.renderPdf(INVOICE_ID, null);

        verify(pdfGenerator).render(eq(invoice), eq(tenant), eq(customer), isNull(), nullable(byte[].class));
    }

    @Test
    void renderPdfRejectsUnknownOverride() {
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.renderPdf(INVOICE_ID, "fancy"))
                .isInstanceOf(AppException.class);
    }
}
