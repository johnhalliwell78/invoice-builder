package com.invoicebuilder.invoice;

import com.invoicebuilder.audit.AuditService;
import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.email.EmailService;
import com.invoicebuilder.invoice.dto.InvoiceListItem;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceListItemsTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_A = UUID.randomUUID();
    private static final UUID CUSTOMER_MISSING = UUID.randomUUID();

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

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoiceRepository, customerRepository, tenantRepository,
                numberGenerator, new InvoiceCalculator(), pdfGenerator, pdfStorage, logoStorage,
                emailService, messages, reminderRepository, auditService, eventPublisher,
                Clock.fixed(Instant.parse("2026-07-21T10:00:00Z"), ZoneOffset.UTC));
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static Invoice invoice(UUID customerId, String number) {
        Invoice invoice = new Invoice();
        invoice.setTenantId(TENANT_ID);
        invoice.setCustomerId(customerId);
        invoice.setInvoiceNumber(number);
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setCurrency("EUR");
        return invoice;
    }

    @Test
    void listItemsResolvesCustomerNamesInOneBatch() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Invoice> page = new PageImpl<>(
                List.of(invoice(CUSTOMER_A, "INV-1"), invoice(CUSTOMER_MISSING, "INV-2")),
                pageable, 2);
        when(invoiceRepository.search(eq(TENANT_ID), any(), any(), any(), any(), eq(pageable)))
                .thenReturn(page);

        Customer customerA = new Customer();
        customerA.setId(CUSTOMER_A);
        customerA.setTenantId(TENANT_ID);
        customerA.setName("Acme GmbH");
        when(customerRepository.findByTenantIdAndIdIn(TENANT_ID, Set.of(CUSTOMER_A, CUSTOMER_MISSING)))
                .thenReturn(List.of(customerA));

        Page<InvoiceListItem> items = service.listItems(null, null, null, null, pageable);

        assertThat(items.getContent()).hasSize(2);
        assertThat(items.getContent().get(0).customerName()).isEqualTo("Acme GmbH");
        assertThat(items.getContent().get(1).customerName()).isNull();
    }

    @Test
    void listItemsSkipsCustomerLookupForEmptyPage() {
        Pageable pageable = PageRequest.of(0, 20);
        when(invoiceRepository.search(eq(TENANT_ID), any(), any(), any(), any(), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        Page<InvoiceListItem> items = service.listItems(null, null, null, null, pageable);

        assertThat(items).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(customerRepository);
    }
}
