package com.invoicebuilder.stripe;

import com.invoicebuilder.common.exception.AppException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyUnitsTest {

    @Test
    void twoDecimalCurrenciesUseCents() {
        assertThat(MoneyUnits.toMinorUnits(new BigDecimal("119.00"), "EUR")).isEqualTo(11900L);
        assertThat(MoneyUnits.toMinorUnits(new BigDecimal("0.01"), "USD")).isEqualTo(1L);
        assertThat(MoneyUnits.fromMinorUnits(11900L, "EUR")).isEqualByComparingTo("119.00");
    }

    @Test
    void zeroDecimalCurrenciesAreNotMultiplied() {
        // The bug this guards: a blanket x100 would charge a JPY customer
        // one hundred times the invoice amount.
        assertThat(MoneyUnits.toMinorUnits(new BigDecimal("5000"), "JPY")).isEqualTo(5000L);
        assertThat(MoneyUnits.toMinorUnits(new BigDecimal("5000.00"), "jpy")).isEqualTo(5000L);
        assertThat(MoneyUnits.fromMinorUnits(5000L, "JPY")).isEqualByComparingTo("5000");
        assertThat(MoneyUnits.toMinorUnits(new BigDecimal("1200"), "KRW")).isEqualTo(1200L);
    }

    @Test
    void threeDecimalCurrenciesUseThousandths() {
        assertThat(MoneyUnits.toMinorUnits(new BigDecimal("10.500"), "KWD")).isEqualTo(10500L);
        assertThat(MoneyUnits.fromMinorUnits(10500L, "KWD")).isEqualByComparingTo("10.500");
    }

    @Test
    void roundTripPreservesTheAmount() {
        for (String currency : new String[] {"EUR", "USD", "JPY", "KWD"}) {
            BigDecimal original = new BigDecimal("42");
            long minor = MoneyUnits.toMinorUnits(original, currency);
            assertThat(MoneyUnits.fromMinorUnits(minor, currency)).isEqualByComparingTo(original);
        }
    }

    @Test
    void amountsThatCannotBeChargedExactlyAreRejected() {
        // Sub-cent precision would have to be rounded — charging a different
        // amount than the invoice states is a bug, not a rounding decision.
        assertThatThrownBy(() -> MoneyUnits.toMinorUnits(new BigDecimal("1.005"), "EUR"))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> MoneyUnits.toMinorUnits(new BigDecimal("10.50"), "JPY"))
                .isInstanceOf(AppException.class);
    }

    @Test
    void unknownCurrenciesDefaultToTwoDecimals() {
        assertThat(MoneyUnits.exponent("ZZZ")).isEqualTo(2);
        assertThat(MoneyUnits.exponent(null)).isEqualTo(2);
    }
}
