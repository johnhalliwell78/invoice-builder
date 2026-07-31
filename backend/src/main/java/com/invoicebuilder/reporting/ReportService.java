package com.invoicebuilder.reporting;

import com.invoicebuilder.reporting.dto.TaxSummaryRow;
import com.invoicebuilder.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ReportService {

    private final InvoiceLineItemReportRepository reportRepository;

    public ReportService(InvoiceLineItemReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    @Transactional(readOnly = true)
    public List<TaxSummaryRow> taxSummary(LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.require();
        return reportRepository.taxSummary(tenantId, from, to).stream()
                .map(ReportService::toRow)
                .toList();
    }

    private static TaxSummaryRow toRow(Object[] row) {
        BigDecimal net = scale(row[2]);
        BigDecimal tax = scale(row[3]);
        return new TaxSummaryRow((String) row[0], scale(row[1]), net, tax, net.add(tax));
    }

    private static BigDecimal scale(Object value) {
        BigDecimal decimal = value == null ? BigDecimal.ZERO : (BigDecimal) value;
        return decimal.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
