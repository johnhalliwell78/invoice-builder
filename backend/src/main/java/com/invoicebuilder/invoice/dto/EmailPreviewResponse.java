package com.invoicebuilder.invoice.dto;

import com.invoicebuilder.invoice.InvoiceService;

/** Default email content for an invoice, used to prefill the send dialog. */
public record EmailPreviewResponse(String recipientEmail, String subject, String body) {

    public static EmailPreviewResponse from(InvoiceService.EmailPreview preview) {
        return new EmailPreviewResponse(preview.recipientEmail(), preview.subject(), preview.body());
    }
}
