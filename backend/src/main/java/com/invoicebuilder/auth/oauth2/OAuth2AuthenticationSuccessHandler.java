package com.invoicebuilder.auth.oauth2;

import com.invoicebuilder.auth.RefreshTokenService;
import com.invoicebuilder.auth.jwt.JwtService;
import com.invoicebuilder.config.AppProperties;
import com.invoicebuilder.user.AppUser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * After a successful OAuth2 login, mints a JWT access token + refresh cookie
 * and redirects to the SPA's callback URL with the access token in the URL
 * fragment (so it never reaches server logs).
 */
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);
    private static final String REFRESH_COOKIE_NAME = "ib_refresh";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AppProperties appProperties;

    public OAuth2AuthenticationSuccessHandler(JwtService jwtService,
                                              RefreshTokenService refreshTokenService,
                                              AppProperties appProperties) {
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.appProperties = appProperties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (response.isCommitted()) {
            log.warn("OAuth2 success but response already committed");
            return;
        }

        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
        AppUser user = principal.getAppUser();

        String accessToken = jwtService.generateAccessToken(user);
        RefreshTokenService.IssuedToken refresh = refreshTokenService.issue(user.getId());
        long expiresIn = appProperties.jwt().accessTokenExpiry().toSeconds();

        long maxAge = appProperties.jwt().refreshTokenExpiry().toSeconds();
        String cookie = "%s=%s; Max-Age=%d; Path=%s; HttpOnly; Secure; SameSite=Lax".formatted(
                REFRESH_COOKIE_NAME, refresh.rawToken(), maxAge, REFRESH_COOKIE_PATH);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie);

        String redirectUri = UriComponentsBuilder
                .fromUriString(appProperties.oauth2().successRedirectUri())
                .fragment("access_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
                        + "&token_type=Bearer&expires_in=" + expiresIn)
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUri);
    }
}
