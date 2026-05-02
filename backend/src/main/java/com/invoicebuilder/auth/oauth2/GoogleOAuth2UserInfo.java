package com.invoicebuilder.auth.oauth2;

import com.invoicebuilder.user.AuthProvider;

import java.util.Map;

public class GoogleOAuth2UserInfo extends OAuth2UserInfo {

    public GoogleOAuth2UserInfo(Map<String, Object> attributes) {
        super(attributes);
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    public String providerId() {
        return (String) attributes.get("sub");
    }

    @Override
    public String email() {
        return (String) attributes.get("email");
    }

    @Override
    public String fullName() {
        return (String) attributes.get("name");
    }

    @Override
    public String avatarUrl() {
        return (String) attributes.get("picture");
    }
}
