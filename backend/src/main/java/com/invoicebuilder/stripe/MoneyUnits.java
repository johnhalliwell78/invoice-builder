package com.invoicebuilder.stripe;

import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;

/**
 * Conversion between our {@code BigDecimal} amounts and Stripe's integer
 * minor units.
 *
 * <p>Not every currency has two decimal places. Charging a JPY invoice with a
 * blanket ×100 would bill the customer one hundred times the amount, so the
 * exponent is looked up explicitly.</p>
 */
public final class MoneyUnits {

    /** Currencies Stripe treats as having no minor unit at all. */
    private static final Set<String> ZERO_DECIMAL = Set.of(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA",
            "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF");

    /** Currencies with three decimal places (Stripe requires the last digit to be 0). */
    private static final Set<String> THREE_DECIMAL = Set.of("BHD", "JOD", "KWD", "OMR", "TND");

    private MoneyUnits() {
    }

    public static int exponent(String currency) {
        String code = currency == null ? "" : currency.toUpperCase(Locale.ROOT);
        if (ZERO_DECIMAL.contains(code)) {
            return 0;
        }
        return THREE_DECIMAL.contains(code) ? 3 : 2;
    }

    /**
     * Whether {@code amount} can be charged exactly in {@code currency} — i.e.
     * it has no precision below the currency's minor unit. A fractional JPY
     * amount cannot, and offering to charge it would fail every time.
     */
    public static boolean isRepresentable(BigDecimal amount, String currency) {
        try {
            toMinorUnits(amount, currency);
            return true;
        } catch (AppException e) {
            return false;
        }
    }

    /**
     * Converts a decimal amount to Stripe minor units. Refuses to round: a
     * request that would silently charge a different amount than the invoice
     * says is a bug, not something to paper over.
     */
    public static long toMinorUnits(BigDecimal amount, String currency) {
        BigDecimal scaled = amount.movePointRight(exponent(currency));
        try {
            return scaled.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Amount %s cannot be charged exactly in %s".formatted(amount, currency));
        }
    }

    /** Converts Stripe minor units back to a decimal amount at the currency's scale. */
    public static BigDecimal fromMinorUnits(long minorUnits, String currency) {
        return BigDecimal.valueOf(minorUnits, exponent(currency));
    }
}
