package com.invoicebuilder.apikey.dto;

import com.invoicebuilder.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApiKeyRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull Role role
) {
}
