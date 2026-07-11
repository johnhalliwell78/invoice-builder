package com.invoicebuilder.invoice.dto;

import com.invoicebuilder.invoice.InvoiceReminder;
import com.invoicebuilder.invoice.ReminderType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReminderResponse(
        UUID id,
        String recipient,
        ReminderType type,
        OffsetDateTime sentAt
) {

    public static ReminderResponse from(InvoiceReminder reminder) {
        return new ReminderResponse(
                reminder.getId(),
                reminder.getRecipient(),
                reminder.getType(),
                reminder.getSentAt()
        );
    }
}
