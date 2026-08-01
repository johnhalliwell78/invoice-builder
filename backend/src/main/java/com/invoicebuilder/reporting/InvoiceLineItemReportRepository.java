package com.invoicebuilder.reporting;

import com.invoicebuilder.invoice.InvoiceLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceLineItemReportRepository extends JpaRepository<InvoiceLineItem, UUID> {

    /**
     * Net and tax per (currency, rate) for issued documents in a period.
     *
     * <p>Tax is summed <em>per line</em> with the same HALF_UP scale-2
     * rounding {@code InvoiceCalculator} applies. Computing it on the summed
     * net instead would drift by cents from the invoices themselves, and a
     * tax report that disagrees with the documents it describes is worse
     * than none.</p>
     *
     * <p>Drafts, cancellations, and estimates are excluded — none of them
     * are revenue. Issued credit notes are included <em>negatively</em>:
     * without that, an invoice written off by credit would still be
     * declared as taxable turnover.</p>
     */
    @Query("""
            select i.currency,
                   coalesce(li.taxRate, 0),
                   sum(case when i.docType = com.invoicebuilder.invoice.DocType.CREDIT_NOTE
                            then -li.amount else li.amount end),
                   sum(case when i.docType = com.invoicebuilder.invoice.DocType.CREDIT_NOTE
                            then -round(li.amount * coalesce(li.taxRate, 0) / 100, 2)
                            else round(li.amount * coalesce(li.taxRate, 0) / 100, 2) end)
            from InvoiceLineItem li
              join li.invoice i
            where i.tenantId = :tenantId
              and i.docType in (com.invoicebuilder.invoice.DocType.INVOICE,
                                com.invoicebuilder.invoice.DocType.CREDIT_NOTE)
              and i.status not in (com.invoicebuilder.invoice.InvoiceStatus.DRAFT,
                                   com.invoicebuilder.invoice.InvoiceStatus.CANCELLED)
              and i.issueDate >= :from
              and i.issueDate <= :to
            group by i.currency, coalesce(li.taxRate, 0)
            order by i.currency, coalesce(li.taxRate, 0)
            """)
    List<Object[]> taxSummary(@Param("tenantId") UUID tenantId,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to);
}
