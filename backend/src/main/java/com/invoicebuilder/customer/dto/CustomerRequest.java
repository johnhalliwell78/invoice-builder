package com.invoicebuilder.customer.dto;

import com.invoicebuilder.common.Address;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank @Size(max = 255) String name,

        @Email @Size(max = 255) String email,

        @Size(max = 50) String phone,

        @Size(max = 255) String company,

        Address address,

        @Size(max = 100) String taxId,

        @Size(max = 4000) String notes
) {
}
