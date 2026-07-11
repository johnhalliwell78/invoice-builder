package com.invoicebuilder.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AcceptInviteRequest(
        @NotBlank @Size(max = 255) String fullName,

        @NotBlank
        @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must contain at least one uppercase letter and one digit"
        )
        String password
) {
}
