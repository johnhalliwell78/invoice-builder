package com.invoicebuilder.auth.oauth2;

import com.invoicebuilder.config.AppProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationFailureHandler.class);

    private final AppProperties appProperties;

    public OAuth2AuthenticationFailureHandler(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception)
            throws IOException, ServletException {
        log.warn("OAuth2 authentication failed: {}", exception.getMessage());
        String redirect = UriComponentsBuilder
                .fromUriString(appProperties.oauth2().failureRedirectUri())
                .queryParam("message", exception.getMessage() == null ? "oauth2_failure" : exception.getMessage())
                .build()
                .toUriString();
        getRedirectStrategy().sendRedirect(request, response, redirect);
    }
}
