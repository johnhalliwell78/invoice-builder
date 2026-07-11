package com.invoicebuilder.user.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransferOwnershipRequest(
        @NotNull UUID targetUserId
) {
}
