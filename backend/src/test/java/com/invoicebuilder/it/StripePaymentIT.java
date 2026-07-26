package com.invoicebuilder.it;

import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.invoice.DocType;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceStatus;
import com.invoicebuilder.payment.PaymentMethod;
import com.invoicebuilder.payment.PaymentRepository;
import com.invoicebuilder.payment.PaymentService;
import com.invoicebuilder.tenant.Tenant;
import com.invoicebuilder.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Money can only be booked once. Stripe retries webhook deliveries and may
 * report one payment through several event types, so the external id is the
 * idempotency key — enforced here against the real unique index, not a mock.
 */
class StripePaymentIT extends IntegrationTestBase {

    @Autowired private PaymentService paymentService;
    @Autowired private PaymentRepository paymentRepository;

    @Test
    void redeliveredWebhookCannotBookTheSamePaymentTwice() {
        Tenant tenant = createTenant();
        Customer customer = createCustomer(tenant, "Card Payer");
        Invoice invoice = createDocument(tenant, customer, DocType.INVOICE, "200.00", InvoiceStatus.SENT);

        TenantContext.set(tenant.getId());
        Optional<?> first = paymentService.recordExternal(invoice.getId(), new BigDecimal("200.00"), "EUR",
                PaymentMethod.CARD, "pi_dup_test", "Stripe cs_test");
        Optional<?> replay = paymentService.recordExternal(invoice.getId(), new BigDecimal("200.00"), "EUR",
                PaymentMethod.CARD, "pi_dup_test", "Stripe cs_test");

        assertThat(first).isPresent();
        assertThat(replay).isEmpty();
        assertThat(paymentRepository
                .findByInvoiceIdAndTenantIdOrderByPaidOnDescCreatedAtDesc(invoice.getId(), tenant.getId()))
                .hasSize(1);

        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getAmountPaid()).isEqualByComparingTo("200.00");
        assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void cardPaymentLandingAfterAManualMarkPaidIsStillRecorded() {
        Tenant tenant = createTenant();
        Customer customer = createCustomer(tenant, "Late Payer");
        Invoice invoice = createDocument(tenant, customer, DocType.INVOICE, "100.00", InvoiceStatus.SENT);

        TenantContext.set(tenant.getId());
        // The issuer marks it paid by hand while the card payment is in flight.
        paymentService.markRemainingPaid(invoice.getId());
        // Stripe then confirms money it really did collect — losing it would
        // make our books disagree with the processor.
        Optional<?> booked = paymentService.recordExternal(invoice.getId(), new BigDecimal("100.00"), "EUR",
                PaymentMethod.CARD, "pi_late_test", "Stripe cs_late");

        assertThat(booked).isPresent();
        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.PAID);
        // Visible over-payment: 100 manual + 100 card against a 100 invoice.
        assertThat(reloaded.getAmountPaid()).isEqualByComparingTo("200.00");
        assertThat(paymentRepository
                .findByInvoiceIdAndTenantIdOrderByPaidOnDescCreatedAtDesc(invoice.getId(), tenant.getId()))
                .hasSize(2);
    }
}
