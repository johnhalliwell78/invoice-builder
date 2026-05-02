package com.invoicebuilder.auth.oauth2;

import com.invoicebuilder.user.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * OAuth2 principal backed by a persisted {@link AppUser}, so the success handler
 * can mint JWT tokens without re-querying the provider.
 */
public class CustomOAuth2User implements OAuth2User {

    private final AppUser appUser;
    private final Map<String, Object> attributes;

    public CustomOAuth2User(AppUser appUser, Map<String, Object> attributes) {
        this.appUser = appUser;
        this.attributes = attributes;
    }

    public AppUser getAppUser() {
        return appUser;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + appUser.getRole().name()));
    }

    @Override
    public String getName() {
        return appUser.getId().toString();
    }
}
