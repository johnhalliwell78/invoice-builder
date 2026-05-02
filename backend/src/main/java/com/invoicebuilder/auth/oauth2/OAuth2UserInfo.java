package com.invoicebuilder.auth.oauth2;

import com.invoicebuilder.user.AuthProvider;

import java.util.Map;

/**
 * Provider-agnostic view of an OAuth2 user's identity. Subclasses adapt the
 * attributes returned by Google, GitHub, etc. into the same shape.
 */
public abstract class OAuth2UserInfo {

    protected final Map<String, Object> attributes;

    protected OAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public abstract AuthProvider provider();

    public abstract String providerId();

    public abstract String email();

    public abstract String fullName();

    public abstract String avatarUrl();
}
