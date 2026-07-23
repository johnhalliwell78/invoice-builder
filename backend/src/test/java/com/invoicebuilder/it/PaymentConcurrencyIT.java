package com.invoicebuilder.it;

import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.invoice.DocType;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceStatus;
import com.invoicebuilder.payment.PaymentMethod;
import com.invoicebuilder.payment.PaymentService;
import com.invoicebuilder.payment.dto.PaymentRequest;
import com.invoicebuilder.tenant.Tenant;
import com.invoicebuilder.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for the payment lost-update race: two payments racing the
 * balance check must serialize on the row lock — exactly one wins, and the
 * balance can never be overshot. Unit tests cannot cover this (no real
 * transactions, no real locks).
 */
class PaymentConcurrencyIT extends IntegrationTestBase {

    @Autowired private PaymentService paymentService;

    @Test
    void concurrentPaymentsCannotOvershootTheBalance() throws Exception {
        Tenant tenant = createTenant();
        Customer customer = createCustomer(tenant, "Payer Co");
        Invoice invoice = createDocument(tenant, customer, DocType.INVOICE, "200.00", InvoiceStatus.SENT);

        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Boolean> pay150 = () -> {
            TenantContext.set(tenant.getId());
            try {
                start.await();
                paymentService.record(invoice.getId(), new PaymentRequest(
                        new BigDecimal("150.00"), PaymentMethod.BANK_TRANSFER, null, null));
                return true;
            } catch (AppException e) {
                return false;
            } finally {
                TenantContext.clear();
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = pool.invokeAll(List.of(pay150, pay150));
            long successes = results.stream().filter(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).count();

            // Exactly one 150 fits into a 200 balance.
            assertThat(successes).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getAmountPaid()).isEqualByComparingTo("150.00");
        assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.SENT);

        // Settling the true remainder still works and flips to PAID.
        TenantContext.set(tenant.getId());
        paymentService.record(invoice.getId(), new PaymentRequest(
                new BigDecimal("50.00"), PaymentMethod.CASH, null, null));
        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getStatus())
                .isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void zeroTotalInvoiceCanBeMarkedPaidEndToEnd() {
        Tenant tenant = createTenant();
        Customer customer = createCustomer(tenant, "Goodwill Co");
        Invoice invoice = createDocument(tenant, customer, DocType.INVOICE, "0.00", InvoiceStatus.SENT);

        TenantContext.set(tenant.getId());
        paymentService.markRemainingPaid(invoice.getId());

        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getStatus())
                .isEqualTo(InvoiceStatus.PAID);
    }
}
