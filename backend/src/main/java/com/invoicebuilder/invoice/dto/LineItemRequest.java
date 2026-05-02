package com.invoicebuilder.invoice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record LineItemRequest(
        @NotBlank @Size(max = 500) String description,

        @NotNull
        @DecimalMin(value = "0.001", message = "Quantity must be > 0")
        @Digits(integer = 7, fraction = 3)
        BigDecimal quantity,

        @NotNull
        @Digits(integer = 13, fraction = 2)
        BigDecimal unitPrice,

        @DecimalMin("0.00")
        @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal taxRate,

        @DecimalMin("0.00")
        @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal discountPercent
) {
}
