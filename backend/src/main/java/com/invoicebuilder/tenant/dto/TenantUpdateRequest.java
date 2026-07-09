package com.invoicebuilder.tenant.dto;

import com.invoicebuilder.common.Address;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TenantUpdateRequest(
        @NotBlank @Size(max = 255) String name,

        @Size(min = 3, max = 3) String defaultCurrency,

        @Size(min = 2, max = 5) String defaultLocale,

        @Size(max = 100) String taxId,

        @Pattern(regexp = "^[A-Za-z0-9-]{1,10}$", message = "Prefix may only contain letters, digits, and dashes")
        String invoicePrefix,

        @Pattern(regexp = "^(classic|modern)$", message = "Unknown template")
        String defaultTemplate,

        Address address,

        @Size(max = 2000) String footerText,

        @Size(max = 2000) String paymentInfo,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a #RRGGBB hex value")
        String brandingColor
) {
}
