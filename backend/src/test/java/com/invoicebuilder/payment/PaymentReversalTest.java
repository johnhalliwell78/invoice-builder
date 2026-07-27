package com.invoicebuilder.payment;

import com.invoicebuilder.audit.AuditService;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceRepository;
import com.invoicebuilder.invoice.InvoiceStatus;
import com.invoicebuilder.payment.dto.ReversalRequest;
import com.invoicebuilder.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Money can come back: refunds and chargebacks must reduce what the ledger
 * says was collected, and re-open an invoice that is no longer covered.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentReversalTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-27T10:00:00Z");

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentReversalRepository reversalRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PaymentService service;
    private Invoice invoice;
    private Payment payment;

    @BeforeEach
    void setUp() {
        service = new PaymentService(paymentRepository, reversalRepository, invoiceRepository,
                auditService, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
        TenantContext.set(TENANT_ID);

        invoice = new Invoice();
        invoice.setTenantId(TENANT_ID);
        invoice.setCustomerId(UUID.randomUUID());
        invoice.setInvoiceNumber("INV-2026-0001");
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setCurrency("EUR");
        invoice.setTotal(new BigDecimal("200.00"));
        invoice.setAmountPaid(new BigDecimal("200.00"));
        invoice.setPaidAt(OffsetDateTime.parse("2026-07-20T09:00:00Z"));
        invoice.setIssueDate(LocalDate.of(2026, 7, 1));
        // Not yet due, so a re-opened invoice should land on SENT/VIEWED.
        invoice.setDueDate(LocalDate.of(2026, 8, 31));
        ReflectionTestUtils.setField(invoice, "id", INVOICE_ID);

        payment = new Payment();
        payment.setTenantId(TENANT_ID);
        payment.setInvoiceId(INVOICE_ID);
        payment.setAmount(new BigDecimal("200.00"));
        payment.setMethod(PaymentMethod.CARD);
        payment.setExternalId("pi_test_1");
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);

        when(invoiceRepository.findByIdAndTenantIdForUpdate(INVOICE_ID, TENANT_ID))
                .thenReturn(Optional.of(invoice));
        when(paymentRepository.findByIdAndTenantId(PAYMENT_ID, TENANT_ID))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findByExternalIdAndTenantId("pi_test_1", TENANT_ID))
                .thenReturn(Optional.of(payment));
        when(reversalRepository.save(any(PaymentReversal.class))).thenAnswer(i -> i.getArgument(0));
        when(reversalRepository.sumByPaymentId(PAYMENT_ID)).thenReturn(BigDecimal.ZERO);
        when(reversalRepository.findByInvoiceIdAndTenantIdOrderByCreatedAtDesc(INVOICE_ID, TENANT_ID))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void fullRefundReopensTheInvoiceAndClearsPaidAt() {
        service.recordReversal(PAYMENT_ID, new ReversalRequest(
                new BigDecimal("200.00"), ReversalReason.REFUND, "customer returned goods"));

        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
        assertThat(invoice.getPaidAt()).isNull();
    }

    @Test
    void reopenedInvoicePastItsDueDateBecomesOverdue() {
        invoice.setDueDate(LocalDate.of(2026, 7, 1));

        service.recordReversal(PAYMENT_ID, new ReversalRequest(
                new BigDecimal("200.00"), ReversalReason.REFUND, null));

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);
    }

    @Test
    void reopenedInvoiceThatWasViewedGoesBackToViewed() {
        invoice.setViewedAt(OffsetDateTime.parse("2026-07-10T09:00:00Z"));

        service.recordReversal(PAYMENT_ID, new ReversalRequest(
                new BigDecimal("200.00"), ReversalReason.REFUND, null));

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.VIEWED);
    }

    @Test
    void partialRefundLeavesTheInvoiceOpenForTheRemainder() {
        service.recordReversal(PAYMENT_ID, new ReversalRequest(
                new BigDecimal("50.00"), ReversalReason.REFUND, null));

        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("150.00");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
    }

    @Test
    void reversingMoreThanWasCollectedIsRejected() {
        assertThatThrownBy(() -> service.recordReversal(PAYMENT_ID, new ReversalRequest(
                new BigDecimal("250.00"), ReversalReason.REFUND, null)))
                .isInstanceOf(AppException.class);
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("200.00");
    }

    @Test
    void secondPartialReversalCannotExceedWhatRemains() {
        when(reversalRepository.sumByPaymentId(PAYMENT_ID)).thenReturn(new BigDecimal("150.00"));

        assertThatThrownBy(() -> service.recordReversal(PAYMENT_ID, new ReversalRequest(
                new BigDecimal("100.00"), ReversalReason.REFUND, null)))
                .isInstanceOf(AppException.class);
    }

    // ---------- Stripe-driven reversals ----------

    @Test
    void stripeRefundBooksOnlyTheDeltaBecauseStripeReportsCumulativeTotals() {
        // Stripe's charge.refunded carries amount_refunded as a RUNNING TOTAL.
        // Booking it verbatim after a first partial refund would over-reverse.
        when(reversalRepository.sumByPaymentId(PAYMENT_ID)).thenReturn(new BigDecimal("50.00"));

        service.recordExternalReversal("pi_test_1", new BigDecimal("120.00"), "EUR",
                ReversalReason.REFUND, "re_test_2", "Stripe refund");

        ArgumentCaptor<PaymentReversal> captor = ArgumentCaptor.forClass(PaymentReversal.class);
        verify(reversalRepository).save(captor.capture());
        // 120 cumulative − 50 already reversed = 70 new.
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("70.00");
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("130.00");
    }

    @Test
    void redeliveredRefundEventDoesNothing() {
        when(reversalRepository.existsByExternalId("re_dup")).thenReturn(true);

        Optional<?> result = service.recordExternalReversal("pi_test_1", new BigDecimal("200.00"),
                "EUR", ReversalReason.REFUND, "re_dup", "Stripe refund");

        assertThat(result).isEmpty();
        verify(reversalRepository, never()).save(any());
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("200.00");
    }

    @Test
    void cumulativeTotalAlreadyFullyReversedBooksNothing() {
        when(reversalRepository.sumByPaymentId(PAYMENT_ID)).thenReturn(new BigDecimal("200.00"));

        Optional<?> result = service.recordExternalReversal("pi_test_1", new BigDecimal("200.00"),
                "EUR", ReversalReason.REFUND, "re_same", "Stripe refund");

        assertThat(result).isEmpty();
        verify(reversalRepository, never()).save(any());
    }

    @Test
    void disputeReversesTheWholePaymentAndReopensTheInvoice() {
        service.recordExternalReversal("pi_test_1", new BigDecimal("200.00"), "EUR",
                ReversalReason.DISPUTE, "dp_test_1", "Stripe dispute");

        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
    }

    @Test
    void reversalForAnUnknownPaymentIsRejected() {
        when(paymentRepository.findByExternalIdAndTenantId("pi_unknown", TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordExternalReversal("pi_unknown", new BigDecimal("10.00"),
                "EUR", ReversalReason.REFUND, "re_x", "Stripe"))
                .isInstanceOf(AppException.class);
    }
}
