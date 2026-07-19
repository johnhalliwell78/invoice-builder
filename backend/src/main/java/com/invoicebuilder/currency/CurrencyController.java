package com.invoicebuilder.currency;

import com.invoicebuilder.common.dto.ApiResponse;
import com.invoicebuilder.currency.dto.CurrencyRatesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/currencies")
@Tag(name = "Currencies", description = "Foreign-exchange reference rates")
public class CurrencyController {

    private final CurrencyRateService currencyRateService;

    public CurrencyController(CurrencyRateService currencyRateService) {
        this.currencyRateService = currencyRateService;
    }

    @GetMapping("/rates")
    @Operation(summary = "Latest stored FX rates for a base currency")
    public ApiResponse<CurrencyRatesResponse> rates(@RequestParam(defaultValue = "USD") String base) {
        return ApiResponse.of(new CurrencyRatesResponse(base.toUpperCase(),
                currencyRateService.getRates(base)));
    }
}
