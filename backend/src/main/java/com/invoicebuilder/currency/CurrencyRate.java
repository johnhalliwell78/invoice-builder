package com.invoicebuilder.currency;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "currency_rate")
public class CurrencyRate {

    @EmbeddedId
    private CurrencyRateId id;

    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal rate;

    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt = OffsetDateTime.now();

    protected CurrencyRate() {
    }

    public CurrencyRate(CurrencyRateId id, BigDecimal rate, OffsetDateTime fetchedAt) {
        this.id = id;
        this.rate = rate;
        this.fetchedAt = fetchedAt;
    }

    public CurrencyRateId getId() {
        return id;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public OffsetDateTime getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(OffsetDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
