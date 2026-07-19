package com.invoicebuilder.currency.dto;

import java.math.BigDecimal;
import java.util.Map;

public record CurrencyRatesResponse(String base, Map<String, BigDecimal> rates) {
}
