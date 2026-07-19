package com.invoicebuilder.currency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores FX rates fetched from a {@link CurrencyRateProvider} and serves them
 * from the database. A nightly job refreshes the common base currencies.
 */
@Service
public class CurrencyRateService {

    private static final Logger log = LoggerFactory.getLogger(CurrencyRateService.class);
    /** Base currencies refreshed on schedule. */
    private static final List<String> BASE_CURRENCIES = List.of("USD", "EUR");

    private final CurrencyRateProvider provider;
    private final CurrencyRateRepository repository;
    private final Clock clock;

    public CurrencyRateService(CurrencyRateProvider provider,
                               CurrencyRateRepository repository,
                               Clock clock) {
        this.provider = provider;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public int refresh(String baseCurrency) {
        String base = baseCurrency.toUpperCase();
        Map<String, BigDecimal> rates = provider.fetchRates(base);
        OffsetDateTime now = OffsetDateTime.now(clock);
        int count = 0;
        for (Map.Entry<String, BigDecimal> entry : rates.entrySet()) {
            CurrencyRateId id = new CurrencyRateId(base, entry.getKey().toUpperCase());
            CurrencyRate row = repository.findById(id).orElseGet(() -> new CurrencyRate(id, entry.getValue(), now));
            row.setRate(entry.getValue());
            row.setFetchedAt(now);
            repository.save(row);
            count++;
        }
        log.debug("Refreshed {} rates for base {}", count, base);
        return count;
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getRates(String baseCurrency) {
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        for (CurrencyRate row : repository.findByIdBaseCurrency(baseCurrency.toUpperCase())) {
            rates.put(row.getId().targetCurrency(), row.getRate());
        }
        return rates;
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "UTC")
    public void refreshAll() {
        for (String base : BASE_CURRENCIES) {
            try {
                refresh(base);
            } catch (RuntimeException e) {
                log.warn("Scheduled FX refresh failed for base {}", base, e);
            }
        }
    }
}
