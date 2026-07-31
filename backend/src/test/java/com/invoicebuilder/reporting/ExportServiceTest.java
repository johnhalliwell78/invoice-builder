package com.invoicebuilder.reporting;

import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.invoice.DocType;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceRepository;
import com.invoicebuilder.invoice.InvoiceStatus;
import com.invoicebuilder.payment.PaymentRepository;
import com.invoicebuilder.payment.PaymentReversalRepository;
import com.invoicebuilder.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExportServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentReversalRepository reversalRepository;

    private ExportService service;

    @BeforeEach
    void setUp() {
        service = new ExportService(invoiceRepository, customerRepository,
                paymentRepository, reversalRepository);
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Invoice invoice() {
        Invoice invoice = new Invoice();
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        invoice.setTenantId(TENANT_ID);
        invoice.setCustomerId(CUSTOMER_ID);
        invoice.setInvoiceNumber("INV-2026-0001");
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setCurrency("EUR");
        invoice.setSubtotal(new BigDecimal("100.00"));
        invoice.setTaxTotal(new BigDecimal("19.00"));
        invoice.setDiscountAmount(BigDecimal.ZERO);
        invoice.setTotal(new BigDecimal("119.00"));
        invoice.setAmountPaid(new BigDecimal("19.00"));
        invoice.setIssueDate(LocalDate.of(2026, 7, 1));
        invoice.setDueDate(LocalDate.of(2026, 7, 31));
        return invoice;
    }

    private String exportInvoices() {
        StringWriter out = new StringWriter();
        service.writeInvoices(out, DocType.INVOICE, null, null, null);
        return out.toString();
    }

    @Test
    void writesAHeaderAndOneRowPerInvoiceWithTheOutstandingBalance() {
        when(invoiceRepository.search(eq(TENANT_ID), eq(DocType.INVOICE), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(invoice()), PageRequest.of(0, 500), 1));
        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", CUSTOMER_ID);
        customer.setName("Acme GmbH");
        when(customerRepository.findByTenantIdAndIdIn(TENANT_ID, Set.of(CUSTOMER_ID)))
                .thenReturn(List.of(customer));

        String csv = exportInvoices();

        assertThat(csv).startsWith("Number,Status,Customer,");
        assertThat(csv).contains("INV-2026-0001,SENT,Acme GmbH,EUR,2026-07-01,2026-07-31");
        // 119.00 total − 19.00 paid
        assertThat(csv.trim()).endsWith(",100.00");
    }

    @Test
    void aCustomerNameThatLooksLikeAFormulaCannotExecuteInASpreadsheet() {
        when(invoiceRepository.search(eq(TENANT_ID), eq(DocType.INVOICE), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(invoice()), PageRequest.of(0, 500), 1));
        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", CUSTOMER_ID);
        customer.setName("=cmd|'/c calc'!A1");
        when(customerRepository.findByTenantIdAndIdIn(TENANT_ID, Set.of(CUSTOMER_ID)))
                .thenReturn(List.of(customer));

        assertThat(exportInvoices()).contains("\"'=cmd|'/c calc'!A1\"");
    }

    @Test
    void anEmptyTenantStillGetsAUsableFileWithJustTheHeader() {
        when(invoiceRepository.search(eq(TENANT_ID), eq(DocType.INVOICE), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 500)));

        assertThat(exportInvoices().trim().lines().count()).isEqualTo(1);
    }
}
