package com.invoicebuilder.notification;

/** Kinds of in-app notification. The default title is a server-side fallback; the
 *  frontend localizes its own copy keyed on the type name. */
public enum NotificationType {
    INVOICE_SENT("Invoice sent"),
    INVOICE_VIEWED("Invoice viewed"),
    INVOICE_PAID("Invoice paid"),
    INVOICE_OVERDUE("Invoice overdue"),
    CUSTOMER_CREATED("Customer added");

    private final String defaultTitle;

    NotificationType(String defaultTitle) {
        this.defaultTitle = defaultTitle;
    }

    public String defaultTitle() {
        return defaultTitle;
    }
}
