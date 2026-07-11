package com.invoicebuilder.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InviteRequest(
        @NotBlank @Email String email,

        @NotBlank
        @Pattern(regexp = "^(ADMIN|MEMBER)$", message = "Role must be ADMIN or MEMBER")
        String role
) {
}
