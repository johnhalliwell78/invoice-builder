package com.invoicebuilder.invoice;

import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.email.EmailService;
import com.invoicebuilder.pdf.InvoicePdfGenerator;
import com.invoicebuilder.pdf.PdfStorage;
import com.invoicebuilder.tenant.LogoStorage;
import com.invoicebuilder.tenant.Tenant;
import com.invoicebuilder.tenant.TenantRepository;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceOverdueTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 11);

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private InvoiceNumberGenerator numberGenerator;
    @Mock private InvoiceCalculator calculator;
    @Mock private InvoicePdfGenerator pdfGenerator;
    @Mock private PdfStorage pdfStorage;
    @Mock private LogoStorage logoStorage;
    @Mock private EmailService emailService;
    @Mock private MessageSource messages;
    @Mock private InvoiceReminderRepository reminderRepository;

    private InvoiceService service;
    private Invoice invoice;
    private Tenant tenant;
    private Customer customer;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoiceRepository, customerRepository, tenantRepository,
                numberGenerator, calculator, pdfGenerator, pdfStorage, logoStorage, emailService,
                messages, reminderRepository,
                Clock.fixed(Instant.parse("2026-07-11T03:15:00Z"), ZoneOffset.UTC));

        invoice = new Invoice();
        invoice.setTenantId(TENANT_ID);
        invoice.setCustomerId(CUSTOMER_ID);
        invoice.setInvoiceNumber("INV-2026-0001");
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setIssueDate(LocalDate.of(2026, 6, 1));
        invoice.setDueDate(LocalDate.of(2026, 7, 1));

        tenant = new Tenant();
        tenant.setName("Acme GmbH");
        tenant.setDefaultLocale("en");

        customer = new Customer();
        customer.setName("Widget Co");
        customer.setEmail("billing@widget.example");

        lenient().when(invoiceRepository.findOverdueIds(eq(TENANT_ID), anyList(), eq(TODAY)))
                .thenReturn(List.of(INVOICE_ID));
        lenient().when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
        lenient().when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        lenient().when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        lenient().when(pdfGenerator.render(eq(invoice), eq(tenant), eq(customer), isNull(), nullable(byte[].class)))
                .thenReturn(new byte[]{1});
        lenient().when(messages.getMessage(eq("email.reminder.subject"), any(), any(Locale.class)))
                .thenReturn("Reminder: invoice INV-2026-0001 from Acme GmbH is overdue");
        lenient().when(messages.getMessage(eq("email.reminder.body"), any(), any(Locale.class)))
                .thenReturn("Friendly reminder body");
    }

    @Test
    void marksOverdueSendsReminderAndRecordsHistory() {
        int count = service.markOverdueForTenant(TENANT_ID, TODAY);

        assertThat(count).isEqualTo(1);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);

        ArgumentCaptor<EmailService.EmailMessage> emailCaptor =
                ArgumentCaptor.forClass(EmailService.EmailMessage.class);
        verify(emailService).send(emailCaptor.capture());
        assertThat(emailCaptor.getValue().toEmail()).isEqualTo("billing@widget.example");
        assertThat(emailCaptor.getValue().subject()).contains("Reminder");

        ArgumentCaptor<InvoiceReminder> reminderCaptor = ArgumentCaptor.forClass(InvoiceReminder.class);
        verify(reminderRepository).save(reminderCaptor.capture());
        assertThat(reminderCaptor.getValue().getType()).isEqualTo(ReminderType.AUTO_OVERDUE);
        assertThat(reminderCaptor.getValue().getRecipient()).isEqualTo("billing@widget.example");
        assertThat(reminderCaptor.getValue().getTenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void marksOverdueWithoutEmailWhenCustomerHasNoAddress() {
        customer.setEmail(null);

        int count = service.markOverdueForTenant(TENANT_ID, TODAY);

        assertThat(count).isEqualTo(1);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);
        verifyNoInteractions(emailService, reminderRepository);
    }

    @Test
    void emailFailureDoesNotPreventOverdueTransition() {
        doThrow(new IllegalStateException("smtp down")).when(emailService).send(any());

        int count = service.markOverdueForTenant(TENANT_ID, TODAY);

        assertThat(count).isEqualTo(1);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);
        verifyNoInteractions(reminderRepository);
    }
}
