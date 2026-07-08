package com.invoicebuilder.invoice;

import com.invoicebuilder.common.dto.ApiResponse;
import com.invoicebuilder.common.dto.PageResponse;
import com.invoicebuilder.invoice.dto.InvoiceListItem;
import com.invoicebuilder.invoice.dto.InvoiceRequest;
import com.invoicebuilder.invoice.dto.InvoiceResponse;
import com.invoicebuilder.invoice.dto.SendInvoiceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    @Operation(summary = "List invoices with optional filters")
    public ApiResponse<PageResponse<InvoiceListItem>> list(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.of(PageResponse.of(
                invoiceService.list(status, customerId, from, to, pageable),
                InvoiceListItem::from));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get invoice with line items")
    public ApiResponse<InvoiceResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(InvoiceResponse.from(invoiceService.get(id)));
    }

    @PostMapping
    @Operation(summary = "Create a draft invoice")
    public ResponseEntity<ApiResponse<InvoiceResponse>> create(@Valid @RequestBody InvoiceRequest request) {
        Invoice invoice = invoiceService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(InvoiceResponse.from(invoice)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a draft invoice")
    public ApiResponse<InvoiceResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody InvoiceRequest request) {
        return ApiResponse.of(InvoiceResponse.from(invoiceService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a draft invoice")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        invoiceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/send")
    @Operation(summary = "Send invoice (DRAFT → SENT) with optional email overrides")
    public ApiResponse<InvoiceResponse> send(@PathVariable UUID id,
                                             @Valid @RequestBody(required = false) SendInvoiceRequest request) {
        return ApiResponse.of(InvoiceResponse.from(invoiceService.send(id, request)));
    }

    @PostMapping("/{id}/mark-paid")
    @Operation(summary = "Mark invoice as paid")
    public ApiResponse<InvoiceResponse> markPaid(@PathVariable UUID id) {
        return ApiResponse.of(InvoiceResponse.from(invoiceService.markPaid(id)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel invoice")
    public ApiResponse<InvoiceResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.of(InvoiceResponse.from(invoiceService.cancel(id)));
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Download invoice PDF (attachment)")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        return pdfResponse(id, ContentDisposition.attachment());
    }

    @GetMapping("/{id}/preview")
    @Operation(summary = "Preview invoice PDF inline")
    public ResponseEntity<byte[]> previewPdf(@PathVariable UUID id) {
        return pdfResponse(id, ContentDisposition.inline());
    }

    private ResponseEntity<byte[]> pdfResponse(UUID id, ContentDisposition.Builder disposition) {
        byte[] pdf = invoiceService.renderPdf(id);
        String filename = invoiceService.suggestedFilename(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.filename(filename).build().toString())
                .body(pdf);
    }
}
