package com.invoicebuilder.config;

import java.security.Principal;

/**
 * Principal whose name is the authenticated user's id, so Spring can route
 * {@code convertAndSendToUser(userId, ...)} messages to the right session.
 */
public record StompPrincipal(String name) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
