package com.invoicebuilder.payment.dto;

import com.invoicebuilder.payment.ReversalReason;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ReversalRequest(
        @NotNull
        @DecimalMin(value = "0.01", message = "Reversal amount must be positive")
        @Digits(integer = 13, fraction = 2)
        BigDecimal amount,

        @NotNull ReversalReason reason,

        @Size(max = 500) String note
) {
}
