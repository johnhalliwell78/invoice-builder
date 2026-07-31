package com.invoicebuilder.reporting;

import com.invoicebuilder.common.dto.ApiResponse;
import com.invoicebuilder.invoice.DocType;
import com.invoicebuilder.invoice.InvoiceStatus;
import com.invoicebuilder.reporting.dto.TaxSummaryRow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reports & exports")
public class ReportController {

    private final ReportService reportService;
    private final ExportService exportService;

    public ReportController(ReportService reportService, ExportService exportService) {
        this.reportService = reportService;
        this.exportService = exportService;
    }

    @GetMapping("/reports/tax-summary")
    @Operation(summary = "Net, tax, and gross per tax rate for a period")
    public ApiResponse<List<TaxSummaryRow>> taxSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.of(reportService.taxSummary(from, to));
    }

    @GetMapping(value = "/exports/invoices.csv", produces = "text/csv")
    @Operation(summary = "Export invoices as CSV")
    public ResponseEntity<StreamingResponseBody> exportInvoices(
            @RequestParam(required = false, defaultValue = "INVOICE") DocType docType,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        // The tenant lives in a ThreadLocal, and the body streams after this
        // method returns — capture it now rather than reading it on the
        // writer thread, where it would be gone.
        java.util.UUID tenantId = com.invoicebuilder.tenant.TenantContext.require();
        StreamingResponseBody body = stream -> withTenant(tenantId, () -> {
            Writer out = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
            exportService.writeInvoices(out, docType, status, from, to);
        });
        return csvResponse(body, "invoices.csv");
    }

    @GetMapping(value = "/exports/payments.csv", produces = "text/csv")
    @Operation(summary = "Export payments and reversals as CSV")
    public ResponseEntity<StreamingResponseBody> exportPayments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        java.util.UUID tenantId = com.invoicebuilder.tenant.TenantContext.require();
        StreamingResponseBody body = stream -> withTenant(tenantId, () -> {
            Writer out = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
            exportService.writePayments(out, from, to);
        });
        return csvResponse(body, "payments.csv");
    }

    private static void withTenant(java.util.UUID tenantId, Runnable work) {
        com.invoicebuilder.tenant.TenantContext.set(tenantId);
        try {
            work.run();
        } finally {
            com.invoicebuilder.tenant.TenantContext.clear();
        }
    }

    private static ResponseEntity<StreamingResponseBody> csvResponse(StreamingResponseBody body,
                                                                     String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
