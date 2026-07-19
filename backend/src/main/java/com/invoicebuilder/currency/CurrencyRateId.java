package com.invoicebuilder.currency;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;

@Embeddable
public record CurrencyRateId(
        @Column(name = "base_currency", length = 3) String baseCurrency,
        @Column(name = "target_currency", length = 3) String targetCurrency
) implements Serializable {
}
