package com.invoicebuilder.auth.oauth2;

import com.invoicebuilder.user.AuthProvider;

import java.util.Map;

public class GithubOAuth2UserInfo extends OAuth2UserInfo {

    public GithubOAuth2UserInfo(Map<String, Object> attributes) {
        super(attributes);
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.GITHUB;
    }

    @Override
    public String providerId() {
        Object id = attributes.get("id");
        return id == null ? null : id.toString();
    }

    @Override
    public String email() {
        return (String) attributes.get("email");
    }

    @Override
    public String fullName() {
        String name = (String) attributes.get("name");
        return name != null ? name : (String) attributes.get("login");
    }

    @Override
    public String avatarUrl() {
        return (String) attributes.get("avatar_url");
    }
}
