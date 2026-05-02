package com.invoicebuilder.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,

        @NotBlank
        @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must contain at least one uppercase letter and one digit"
        )
        String password,

        @NotBlank @Size(max = 255) String fullName,

        @NotBlank @Size(max = 255) String tenantName,

        @Size(min = 3, max = 3) String defaultCurrency,

        @Size(min = 2, max = 5) String defaultLocale
) {
}
