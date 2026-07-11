package com.invoicebuilder.user.dto;

public record InviteInfoResponse(
        String email,
        String tenantName
) {
}
