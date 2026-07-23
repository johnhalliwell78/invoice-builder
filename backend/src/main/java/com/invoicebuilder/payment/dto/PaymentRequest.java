package com.invoicebuilder.payment.dto;

import com.invoicebuilder.payment.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentRequest(
        @NotNull
        @DecimalMin(value = "0.01", message = "Payment amount must be positive")
        @Digits(integer = 13, fraction = 2)
        BigDecimal amount,

        @NotNull PaymentMethod method,

        /** Null means today. Backdating is fine; postdating is not. */
        @PastOrPresent LocalDate paidOn,

        @Size(max = 500) String note
) {
}
