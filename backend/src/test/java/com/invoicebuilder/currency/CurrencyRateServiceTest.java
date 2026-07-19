package com.invoicebuilder.currency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyRateServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-14T04:30:00Z"), ZoneOffset.UTC);

    @Mock private CurrencyRateProvider provider;
    @Mock private CurrencyRateRepository repository;

    @Test
    void refreshUpsertsEachFetchedRate() {
        when(provider.fetchRates("USD")).thenReturn(Map.of(
                "EUR", new BigDecimal("0.920000"),
                "GBP", new BigDecimal("0.790000")));
        when(repository.findById(any(CurrencyRateId.class))).thenReturn(Optional.empty());
        CurrencyRateService service = new CurrencyRateService(provider, repository, CLOCK);

        int count = service.refresh("usd");

        assertThat(count).isEqualTo(2);
        verify(repository, times(2)).save(any(CurrencyRate.class));
    }

    @Test
    void getRatesMapsRowsToTargetRatePairs() {
        OffsetDateTime now = OffsetDateTime.now(CLOCK);
        when(repository.findByIdBaseCurrency("EUR")).thenReturn(List.of(
                new CurrencyRate(new CurrencyRateId("EUR", "USD"), new BigDecimal("1.08"), now),
                new CurrencyRate(new CurrencyRateId("EUR", "CHF"), new BigDecimal("0.97"), now)));
        CurrencyRateService service = new CurrencyRateService(provider, repository, CLOCK);

        Map<String, BigDecimal> rates = service.getRates("eur");

        assertThat(rates).containsEntry("USD", new BigDecimal("1.08")).containsEntry("CHF", new BigDecimal("0.97"));
    }

    @Test
    void refreshWithNoRatesIsANoOp() {
        when(provider.fetchRates("USD")).thenReturn(Map.of());
        CurrencyRateService service = new CurrencyRateService(provider, repository, CLOCK);

        assertThat(service.refresh("USD")).isZero();
    }
}
