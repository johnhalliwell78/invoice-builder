package com.invoicebuilder.payment;

import com.invoicebuilder.audit.AuditService;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.invoice.DocType;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceRepository;
import com.invoicebuilder.invoice.InvoiceStatus;
import com.invoicebuilder.payment.dto.PaymentRequest;
import com.invoicebuilder.payment.dto.PaymentResponse;
import com.invoicebuilder.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-22T10:00:00Z");

    @Mock private PaymentRepository paymentRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PaymentService service;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        service = new PaymentService(paymentRepository, invoiceRepository, auditService,
                eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
        TenantContext.set(TENANT_ID);

        invoice = new Invoice();
        invoice.setTenantId(TENANT_ID);
        invoice.setCustomerId(UUID.randomUUID());
        invoice.setInvoiceNumber("INV-2026-0001");
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setCurrency("EUR");
        invoice.setTotal(new BigDecimal("200.00"));
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.setIssueDate(LocalDate.of(2026, 7, 1));
        invoice.setDueDate(LocalDate.of(2026, 7, 31));

        lenient().when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
                .thenReturn(Optional.of(invoice));
        lenient().when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static PaymentRequest request(String amount) {
        return new PaymentRequest(new BigDecimal(amount), PaymentMethod.BANK_TRANSFER,
                LocalDate.of(2026, 7, 20), "wire ref 123");
    }

    @Test
    void partialPaymentAccumulatesWithoutStatusChange() {
        PaymentResponse response = service.record(INVOICE_ID, request("80.00"));

        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("80.00");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
        assertThat(invoice.getPaidAt()).isNull();
        assertThat(response.amount()).isEqualByComparingTo("80.00");
        assertThat(response.method()).isEqualTo(PaymentMethod.BANK_TRANSFER);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().getPaidOn()).isEqualTo(LocalDate.of(2026, 7, 20));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void finalPaymentTransitionsToPaid() {
        invoice.setAmountPaid(new BigDecimal("80.00"));

        service.record(INVOICE_ID, request("120.00"));

        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("200.00");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getPaidAt()).isNotNull();
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void overpaymentIsRejected() {
        invoice.setAmountPaid(new BigDecimal("150.00"));

        assertThatThrownBy(() -> service.record(INVOICE_ID, request("60.00")))
                .isInstanceOf(AppException.class);
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("150.00");
    }

    @Test
    void paymentsOnlyApplyToOpenInvoices() {
        invoice.setStatus(InvoiceStatus.DRAFT);
        assertThatThrownBy(() -> service.record(INVOICE_ID, request("50.00")))
                .isInstanceOf(AppException.class);

        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setDocType(DocType.ESTIMATE);
        assertThatThrownBy(() -> service.record(INVOICE_ID, request("50.00")))
                .isInstanceOf(AppException.class);
    }

    @Test
    void paidOnDefaultsToToday() {
        service.record(INVOICE_ID, new PaymentRequest(new BigDecimal("10.00"),
                PaymentMethod.CASH, null, null));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getPaidOn()).isEqualTo(LocalDate.of(2026, 7, 22));
    }

    @Test
    void markRemainingPaidRecordsTheBalanceAsPayment() {
        invoice.setAmountPaid(new BigDecimal("80.00"));

        service.markRemainingPaid(INVOICE_ID);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("120.00");
        assertThat(captor.getValue().getMethod()).isEqualTo(PaymentMethod.OTHER);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("200.00");
    }
}
