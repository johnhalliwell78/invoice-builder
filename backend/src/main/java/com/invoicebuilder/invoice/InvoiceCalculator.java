package com.invoicebuilder.invoice;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Recomputes monetary totals on an invoice and its line items.
 * All math runs server-side regardless of what the client sends so that the
 * stored totals are tamper-proof.
 */
@Component
public class InvoiceCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    public Totals recompute(Invoice invoice, BigDecimal discountAmount) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;

        List<InvoiceLineItem> items = invoice.getLineItems();
        for (InvoiceLineItem item : items) {
            BigDecimal quantity = nz(item.getQuantity()).max(BigDecimal.ZERO);
            BigDecimal unitPrice = nz(item.getUnitPrice());
            BigDecimal discountPct = nz(item.getDiscountPercent()).max(BigDecimal.ZERO).min(HUNDRED);
            BigDecimal taxRate = nz(item.getTaxRate()).max(BigDecimal.ZERO);

            BigDecimal gross = quantity.multiply(unitPrice);
            BigDecimal discount = gross.multiply(discountPct).divide(HUNDRED, 4, RoundingMode.HALF_UP);
            BigDecimal lineAmount = gross.subtract(discount).setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal lineTax = lineAmount.multiply(taxRate).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);

            item.setAmount(lineAmount);
            subtotal = subtotal.add(lineAmount);
            taxTotal = taxTotal.add(lineTax);
        }

        BigDecimal flatDiscount = nz(discountAmount).max(BigDecimal.ZERO);
        BigDecimal total = subtotal.add(taxTotal).subtract(flatDiscount);
        if (total.signum() < 0) {
            total = BigDecimal.ZERO;
        }

        return new Totals(
                subtotal.setScale(SCALE, RoundingMode.HALF_UP),
                taxTotal.setScale(SCALE, RoundingMode.HALF_UP),
                flatDiscount.setScale(SCALE, RoundingMode.HALF_UP),
                total.setScale(SCALE, RoundingMode.HALF_UP)
        );
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public record Totals(BigDecimal subtotal, BigDecimal taxTotal, BigDecimal discountAmount, BigDecimal total) {
    }
}
