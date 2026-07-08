package com.invoicebuilder.invoice;

import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.email.EmailService;
import com.invoicebuilder.invoice.dto.SendInvoiceRequest;
import com.invoicebuilder.pdf.InvoicePdfGenerator;
import com.invoicebuilder.pdf.PdfStorage;
import com.invoicebuilder.tenant.Tenant;
import com.invoicebuilder.tenant.TenantContext;
import com.invoicebuilder.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceSendTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final byte[] PDF = {1, 2, 3};
    private static final Instant NOW = Instant.parse("2026-07-08T12:00:00Z");

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private InvoiceNumberGenerator numberGenerator;
    @Mock private InvoiceCalculator calculator;
    @Mock private InvoicePdfGenerator pdfGenerator;
    @Mock private PdfStorage pdfStorage;
    @Mock private EmailService emailService;
    @Mock private MessageSource messages;

    private InvoiceService service;
    private Invoice invoice;
    private Tenant tenant;
    private Customer customer;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoiceRepository, customerRepository, tenantRepository,
                numberGenerator, calculator, pdfGenerator, pdfStorage, emailService, messages,
                Clock.fixed(NOW, ZoneOffset.UTC));
        TenantContext.set(TENANT_ID);

        invoice = new Invoice();
        invoice.setTenantId(TENANT_ID);
        invoice.setCustomerId(CUSTOMER_ID);
        invoice.setInvoiceNumber("INV-2026-0001");
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setIssueDate(LocalDate.of(2026, 7, 1));
        invoice.setDueDate(LocalDate.of(2026, 8, 1));

        tenant = new Tenant();
        tenant.setName("Acme GmbH");
        tenant.setDefaultLocale("en");

        customer = new Customer();
        customer.setName("Widget Co");
        customer.setEmail("billing@widget.example");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void stubInvoiceLookup() {
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
                .thenReturn(Optional.of(invoice));
    }

    private void stubParties() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
    }

    private void stubPdf() {
        when(pdfGenerator.render(invoice, tenant, customer)).thenReturn(PDF);
    }

    private void stubDefaultMessages() {
        lenient().when(messages.getMessage(eq("email.invoice.subject"), any(), any(Locale.class)))
                .thenReturn("Invoice INV-2026-0001 from Acme GmbH");
        lenient().when(messages.getMessage(eq("email.invoice.bodyDefault"), any(), any(Locale.class)))
                .thenReturn("Hi, please find your invoice attached.");
    }

    @Test
    void sendAppliesCustomRecipientSubjectMessageAndCcBcc() {
        stubInvoiceLookup();
        stubParties();
        stubPdf();
        SendInvoiceRequest request = new SendInvoiceRequest(
                "custom@example.com", List.of("cc@example.com"), List.of("bcc@example.com"),
                "Custom subject", "Custom message", null);

        service.send(INVOICE_ID, request);

        ArgumentCaptor<EmailService.EmailMessage> captor =
                ArgumentCaptor.forClass(EmailService.EmailMessage.class);
        verify(emailService).send(captor.capture());
        EmailService.EmailMessage sent = captor.getValue();
        assertThat(sent.toEmail()).isEqualTo("custom@example.com");
        assertThat(sent.cc()).containsExactly("cc@example.com");
        assertThat(sent.bcc()).containsExactly("bcc@example.com");
        assertThat(sent.subject()).isEqualTo("Custom subject");
        assertThat(sent.plainTextBody()).isEqualTo("Custom message");
        assertThat(sent.attachmentName()).isEqualTo("invoice-INV-2026-0001.pdf");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
        assertThat(invoice.getSentAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(invoice.getPublicToken()).isNotBlank();
    }

    @Test
    void sendFallsBackToCustomerEmailAndLocalizedDefaults() {
        stubInvoiceLookup();
        stubParties();
        stubPdf();
        stubDefaultMessages();

        service.send(INVOICE_ID, null);

        ArgumentCaptor<EmailService.EmailMessage> captor =
                ArgumentCaptor.forClass(EmailService.EmailMessage.class);
        verify(emailService).send(captor.capture());
        assertThat(captor.getValue().toEmail()).isEqualTo("billing@widget.example");
        assertThat(captor.getValue().subject()).isEqualTo("Invoice INV-2026-0001 from Acme GmbH");
        assertThat(captor.getValue().cc()).isEmpty();
    }

    @Test
    void sendWithSkipEmailTransitionsWithoutSending() {
        stubInvoiceLookup();
        SendInvoiceRequest request = new SendInvoiceRequest(null, null, null, null, null, true);

        service.send(INVOICE_ID, request);

        verifyNoInteractions(emailService);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
    }

    @Test
    void sendSilentlySkipsEmailWhenNoRecipientAvailable() {
        stubInvoiceLookup();
        stubParties();
        stubDefaultMessages();
        customer.setEmail(null);

        service.send(INVOICE_ID, null);

        verifyNoInteractions(emailService);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
    }

    @Test
    void sendRejectsNonDraftInvoice() {
        stubInvoiceLookup();
        invoice.setStatus(InvoiceStatus.PAID);

        assertThatThrownBy(() -> service.send(INVOICE_ID, null))
                .isInstanceOf(AppException.class);
        verifyNoInteractions(emailService);
    }
}
