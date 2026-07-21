package com.invoicebuilder.product.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 255) String name,

        @Size(max = 4000) String description,

        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal unitPrice,

        @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal taxRate,

        @Size(max = 100) String category,

        /** Null on create means active; on update, null leaves the flag unchanged. */
        Boolean active
) {
}
