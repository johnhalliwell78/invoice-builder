package com.invoicebuilder.auth.dto;

import com.invoicebuilder.user.AppUser;
import com.invoicebuilder.user.AuthProvider;
import com.invoicebuilder.user.Role;

import java.util.UUID;

public record UserResponse(
        UUID id,
        UUID tenantId,
        String email,
        String fullName,
        String avatarUrl,
        Role role,
        AuthProvider provider,
        String locale
) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getTenantId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getProvider(),
                user.getLocale()
        );
    }
}
