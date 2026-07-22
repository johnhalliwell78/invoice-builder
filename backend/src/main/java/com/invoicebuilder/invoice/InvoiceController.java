package com.invoicebuilder.invoice;

import com.invoicebuilder.common.dto.ApiResponse;
import com.invoicebuilder.payment.PaymentService;
import com.invoicebuilder.payment.dto.PaymentRequest;
import com.invoicebuilder.payment.dto.PaymentResponse;
import com.invoicebuilder.recurring.RecurringInvoiceService;
import com.invoicebuilder.recurring.dto.MakeRecurringRequest;
import com.invoicebuilder.recurring.dto.RecurringInvoiceResponse;
import com.invoicebuilder.common.dto.PageResponse;
import com.invoicebuilder.invoice.dto.EmailPreviewResponse;
import com.invoicebuilder.invoice.dto.InvoiceListItem;
import com.invoicebuilder.invoice.dto.InvoiceRequest;
import com.invoicebuilder.invoice.dto.InvoiceResponse;
import com.invoicebuilder.invoice.dto.ReminderResponse;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final RecurringInvoiceService recurringService;
    private final PaymentService paymentService;

    public InvoiceController(InvoiceService invoiceService,
                             RecurringInvoiceService recurringService,
                             PaymentService paymentService) {
        this.invoiceService = invoiceService;
        this.recurringService = recurringService;
        this.paymentService = paymentService;
    }

    @GetMapping
    @Operation(summary = "List invoices with optional filters")
    public ApiResponse<PageResponse<InvoiceListItem>> list(
            @RequestParam(required = false, defaultValue = "INVOICE") DocType docType,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.of(PageResponse.of(
                invoiceService.listItems(docType, status, customerId, from, to, pageable)));
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

    @PostMapping("/{id}/payments")
    @Operation(summary = "Record a payment against an open invoice (append-only)")
    public ResponseEntity<ApiResponse<PaymentResponse>> recordPayment(
            @PathVariable UUID id, @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(paymentService.record(id, request)));
    }

    @GetMapping("/{id}/payments")
    @Operation(summary = "List payments received for an invoice")
    public ApiResponse<List<PaymentResponse>> listPayments(@PathVariable UUID id) {
        return ApiResponse.of(paymentService.list(id));
    }

    @PostMapping("/{id}/make-recurring")
    @Operation(summary = "Create a recurring schedule from this invoice")
    public ResponseEntity<ApiResponse<RecurringInvoiceResponse>> makeRecurring(
            @PathVariable UUID id, @Valid @RequestBody MakeRecurringRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(recurringService.makeRecurring(id, request)));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve an open estimate")
    public ApiResponse<InvoiceResponse> approve(@PathVariable UUID id) {
        return ApiResponse.of(InvoiceResponse.from(invoiceService.approve(id)));
    }

    @PostMapping("/{id}/decline")
    @Operation(summary = "Decline an open estimate")
    public ApiResponse<InvoiceResponse> decline(@PathVariable UUID id) {
        return ApiResponse.of(InvoiceResponse.from(invoiceService.decline(id)));
    }

    @PostMapping("/{id}/convert")
    @Operation(summary = "Convert an APPROVED estimate into a fresh DRAFT invoice")
    public ResponseEntity<ApiResponse<InvoiceResponse>> convert(@PathVariable UUID id) {
        Invoice invoice = invoiceService.convert(id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(InvoiceResponse.from(invoice)));
    }

    @PostMapping("/{id}/duplicate")
    @Operation(summary = "Duplicate any invoice into a fresh DRAFT (dates shifted to today)")
    public ResponseEntity<ApiResponse<InvoiceResponse>> duplicate(@PathVariable UUID id) {
        Invoice copy = invoiceService.duplicate(id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(InvoiceResponse.from(copy)));
    }

    @PostMapping("/{id}/resend")
    @Operation(summary = "Resend the invoice email (status unchanged)")
    public ApiResponse<InvoiceResponse> resend(@PathVariable UUID id,
                                               @Valid @RequestBody(required = false) SendInvoiceRequest request) {
        return ApiResponse.of(InvoiceResponse.from(invoiceService.resend(id, request)));
    }

    @PostMapping("/{id}/mark-paid")
    @Operation(summary = "Mark invoice as paid")
    public ApiResponse<InvoiceResponse> markPaid(@PathVariable UUID id) {
        paymentService.markRemainingPaid(id);
        return ApiResponse.of(InvoiceResponse.from(invoiceService.get(id)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel invoice")
    public ApiResponse<InvoiceResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.of(InvoiceResponse.from(invoiceService.cancel(id)));
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Download invoice PDF (attachment), optionally with a template override")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id,
                                              @RequestParam(required = false) String template) {
        return pdfResponse(id, ContentDisposition.attachment(), template);
    }

    @GetMapping("/{id}/preview")
    @Operation(summary = "Preview invoice PDF inline, optionally with a template override")
    public ResponseEntity<byte[]> previewPdf(@PathVariable UUID id,
                                             @RequestParam(required = false) String template) {
        return pdfResponse(id, ContentDisposition.inline(), template);
    }

    @GetMapping("/{id}/email-preview")
    @Operation(summary = "Preview the invoice email content (recipient, subject, body)")
    public ApiResponse<EmailPreviewResponse> emailPreview(@PathVariable UUID id) {
        return ApiResponse.of(EmailPreviewResponse.from(invoiceService.previewEmail(id)));
    }

    @GetMapping("/{id}/reminders")
    @Operation(summary = "Reminder history (automatic overdue reminders and manual resends)")
    public ApiResponse<List<ReminderResponse>> reminders(@PathVariable UUID id) {
        return ApiResponse.of(invoiceService.listReminders(id).stream()
                .map(ReminderResponse::from).toList());
    }

    private ResponseEntity<byte[]> pdfResponse(UUID id, ContentDisposition.Builder disposition,
                                               String templateOverride) {
        byte[] pdf = invoiceService.renderPdf(id, templateOverride);
        String filename = invoiceService.suggestedFilename(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.filename(filename).build().toString())
                .body(pdf);
    }
}
