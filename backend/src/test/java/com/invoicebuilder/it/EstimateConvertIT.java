package com.invoicebuilder.it;

import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.invoice.DocType;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceStatus;
import com.invoicebuilder.tenant.Tenant;
import com.invoicebuilder.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for the double-conversion race: converting one APPROVED estimate
 * from two requests at once must yield exactly ONE invoice — the row lock
 * turns the second attempt into an already-converted rejection.
 */
class EstimateConvertIT extends IntegrationTestBase {

    @Test
    void concurrentConvertYieldsExactlyOneInvoice() throws Exception {
        Tenant tenant = createTenant();
        Customer customer = createCustomer(tenant, "Prospect Co");
        Invoice estimate = createDocument(tenant, customer, DocType.ESTIMATE, "500.00",
                InvoiceStatus.APPROVED);

        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Boolean> convert = () -> {
            TenantContext.set(tenant.getId());
            try {
                start.await();
                invoiceService.convert(estimate.getId());
                return true;
            } catch (AppException e) {
                return false;
            } finally {
                TenantContext.clear();
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        long successes;
        try {
            List<Future<Boolean>> results = pool.invokeAll(List.of(convert, convert));
            successes = results.stream().filter(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).count();
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes).isEqualTo(1);

        // Exactly one INVOICE exists for this tenant, and the estimate links to it.
        var invoices = invoiceRepository.search(tenant.getId(), DocType.INVOICE, null, null, null, null,
                Pageable.unpaged());
        assertThat(invoices.getTotalElements()).isEqualTo(1);
        Invoice reloaded = invoiceRepository.findById(estimate.getId()).orElseThrow();
        assertThat(reloaded.getConvertedInvoiceId())
                .isEqualTo(invoices.getContent().get(0).getId());
    }
}
