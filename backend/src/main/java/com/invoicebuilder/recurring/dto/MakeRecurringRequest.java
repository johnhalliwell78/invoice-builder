package com.invoicebuilder.recurring.dto;

import com.invoicebuilder.recurring.Frequency;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record MakeRecurringRequest(
        @NotNull Frequency frequency,

        boolean autoSend,

        /** First generation date; null means one period after today. */
        LocalDate firstRun
) {
}
