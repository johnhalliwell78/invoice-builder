package com.invoicebuilder.user.dto;

import com.invoicebuilder.user.AppUser;
import com.invoicebuilder.user.AuthProvider;
import com.invoicebuilder.user.Role;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MemberResponse(
        UUID id,
        String email,
        String fullName,
        Role role,
        AuthProvider provider,
        boolean active,
        boolean pendingInvite,
        OffsetDateTime lastLoginAt,
        OffsetDateTime invitedAt
) {

    public static MemberResponse from(AppUser user) {
        return new MemberResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getProvider(),
                user.isActive(),
                user.getInviteTokenHash() != null,
                user.getLastLoginAt(),
                user.getInvitedAt()
        );
    }
}
