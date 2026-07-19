package com.invoicebuilder.currency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Fetches rates from the free, key-less open.er-api.com endpoint. Best-effort:
 * any network or parsing failure yields an empty map, so a bad fetch never
 * breaks the scheduled refresh.
 */
@Component
public class HttpCurrencyRateProvider implements CurrencyRateProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpCurrencyRateProvider.class);

    private final RestClient restClient;

    public HttpCurrencyRateProvider(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://open.er-api.com/v6/latest").build();
    }

    @Override
    public Map<String, BigDecimal> fetchRates(String baseCurrency) {
        try {
            ErApiResponse response = restClient.get()
                    .uri("/{base}", baseCurrency)
                    .retrieve()
                    .body(ErApiResponse.class);
            if (response == null || response.rates() == null) {
                return Map.of();
            }
            return response.rates();
        } catch (RestClientException e) {
            log.warn("Failed to fetch FX rates for base {}", baseCurrency, e);
            return Map.of();
        }
    }

    record ErApiResponse(String result, Map<String, BigDecimal> rates) {
    }
}
