package com.invoicebuilder.currency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, CurrencyRateId> {

    List<CurrencyRate> findByIdBaseCurrency(String baseCurrency);
}
