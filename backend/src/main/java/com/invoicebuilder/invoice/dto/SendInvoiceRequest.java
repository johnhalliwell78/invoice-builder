package com.invoicebuilder.invoice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Optional overrides for the generated email when sending an invoice. Any
 * absent field falls back to a localized default rendered from the i18n
 * bundle ({@code email.invoice.subject} / {@code email.invoice.bodyDefault}).
 */
public record SendInvoiceRequest(
        @Email @Size(max = 255) String recipientEmail,
        @Size(max = 10) List<@Email @Size(max = 255) String> cc,
        @Size(max = 10) List<@Email @Size(max = 255) String> bcc,
        @Size(max = 255) String subject,
        @Size(max = 4000) String message,
        Boolean skipEmail
) {
    public boolean isEmailSkipped() {
        return Boolean.TRUE.equals(skipEmail);
    }

    public List<String> ccOrEmpty() {
        return cc == null ? List.of() : cc;
    }

    public List<String> bccOrEmpty() {
        return bcc == null ? List.of() : bcc;
    }
}
