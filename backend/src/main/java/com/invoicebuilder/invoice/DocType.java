package com.invoicebuilder.invoice;

/**
 * Discriminator for rows in the {@code invoice} table: a real invoice or an
 * estimate (quote). Estimates share the calculator, PDF, and send pipeline
 * but follow an approval lifecycle and their own number series.
 */
public enum DocType {
    INVOICE,
    ESTIMATE
}
