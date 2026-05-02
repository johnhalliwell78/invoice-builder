package com.invoicebuilder.auth.oauth2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the OAuth2 {@link ClientRegistrationRepository} programmatically so
 * the application boots when no OAuth2 credentials are configured. Each
 * provider is only registered if its env-var credentials are present.
 */
@Configuration
@Conditional(OAuth2ClientConfig.AnyProviderConfigured.class)
public class OAuth2ClientConfig {

    private static final Logger log = LoggerFactory.getLogger(OAuth2ClientConfig.class);
    private static final String REDIRECT_URI = "{baseUrl}/api/v1/auth/oauth2/callback/{registrationId}";

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${GOOGLE_CLIENT_ID:}") String googleClientId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String googleClientSecret,
            @Value("${GITHUB_CLIENT_ID:}") String githubClientId,
            @Value("${GITHUB_CLIENT_SECRET:}") String githubClientSecret) {

        List<ClientRegistration> registrations = new ArrayList<>();
        if (StringUtils.hasText(googleClientId)) {
            registrations.add(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId(googleClientId)
                    .clientSecret(googleClientSecret)
                    .redirectUri(REDIRECT_URI)
                    .scope("openid", "profile", "email")
                    .build());
            log.info("OAuth2 Google client registered");
        }
        if (StringUtils.hasText(githubClientId)) {
            registrations.add(CommonOAuth2Provider.GITHUB.getBuilder("github")
                    .clientId(githubClientId)
                    .clientSecret(githubClientSecret)
                    .redirectUri(REDIRECT_URI)
                    .scope("read:user", "user:email")
                    .build());
            log.info("OAuth2 GitHub client registered");
        }
        return new InMemoryClientRegistrationRepository(registrations);
    }

    /** Activate only if at least one provider has a client-id set. */
    static class AnyProviderConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var env = context.getEnvironment();
            return StringUtils.hasText(env.getProperty("GOOGLE_CLIENT_ID"))
                    || StringUtils.hasText(env.getProperty("GITHUB_CLIENT_ID"));
        }
    }
}
