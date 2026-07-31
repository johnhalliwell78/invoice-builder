package com.invoicebuilder.it;

import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.invoice.DocType;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceStatus;
import com.invoicebuilder.reporting.ReportService;
import com.invoicebuilder.reporting.dto.TaxSummaryRow;
import com.invoicebuilder.tenant.Tenant;
import com.invoicebuilder.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tax summary is aggregated in the database, so only a real one can prove
 * the query works — and that its per-line rounding matches what the invoices
 * themselves say.
 */
class TaxSummaryIT extends IntegrationTestBase {

    @Autowired private ReportService reportService;

    @Test
    void groupsByRateAndReconcilesWithTheInvoiceTotals() {
        Tenant tenant = createTenant();
        Customer customer = createCustomer(tenant, "Report Co");
        Invoice a = createDocument(tenant, customer, DocType.INVOICE, "100.00", InvoiceStatus.SENT);
        Invoice b = createDocument(tenant, customer, DocType.INVOICE, "50.00", InvoiceStatus.PAID);
        // Neither of these is revenue and both must be excluded.
        createDocument(tenant, customer, DocType.INVOICE, "999.00", InvoiceStatus.DRAFT);
        createDocument(tenant, customer, DocType.ESTIMATE, "777.00", InvoiceStatus.SENT);

        TenantContext.set(tenant.getId());
        List<TaxSummaryRow> rows = reportService.taxSummary(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

        assertThat(rows).hasSize(1);
        TaxSummaryRow row = rows.get(0);
        assertThat(row.currency()).isEqualTo("EUR");
        assertThat(row.net()).isEqualByComparingTo("150.00");
        // The fixture uses a 0% rate, so tax is zero and gross equals net.
        assertThat(row.gross()).isEqualByComparingTo(row.net().add(row.tax()));
        // Reconciles against the documents themselves.
        assertThat(row.net()).isEqualByComparingTo(a.getSubtotal().add(b.getSubtotal()));
    }

    @Test
    void oneTenantsFiguresNeverLeakIntoAnothers() {
        Tenant mine = createTenant();
        Tenant theirs = createTenant();
        createDocument(mine, createCustomer(mine, "Mine"), DocType.INVOICE, "10.00", InvoiceStatus.SENT);
        createDocument(theirs, createCustomer(theirs, "Theirs"), DocType.INVOICE, "999.00", InvoiceStatus.SENT);

        TenantContext.set(mine.getId());
        List<TaxSummaryRow> rows = reportService.taxSummary(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).net()).isEqualByComparingTo("10.00");
    }
}
