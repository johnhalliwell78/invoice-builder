package com.invoicebuilder.invoice;

/** Why an invoice email was (re-)delivered after the initial send. */
public enum ReminderType {
    /** Automatic reminder sent when the overdue sweep flags the invoice. */
    AUTO_OVERDUE,
    /** User clicked Resend on the invoice detail page. */
    MANUAL_RESEND
}
