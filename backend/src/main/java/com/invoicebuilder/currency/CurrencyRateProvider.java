package com.invoicebuilder.currency;

import java.math.BigDecimal;
import java.util.Map;

/** Fetches current FX rates for a base currency. Abstracted so the HTTP source
 *  is swappable and the service is testable without network access. */
public interface CurrencyRateProvider {

    /** target-currency → rate relative to {@code baseCurrency}; empty on failure. */
    Map<String, BigDecimal> fetchRates(String baseCurrency);
}
