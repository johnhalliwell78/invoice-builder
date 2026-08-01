package com.invoicebuilder.apikey;

import com.invoicebuilder.auth.UserPrincipal;
import com.invoicebuilder.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates requests carrying {@code X-API-Key}.
 *
 * <p>Runs ahead of the JWT filter and only acts when no authentication is
 * already present, so the two schemes coexist without competing. Actions are
 * attributed to the user who created the key, which keeps the audit trail
 * pointing at a real person rather than an anonymous credential.</p>
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        try {
            String presented = request.getHeader(HEADER);
            if (presented != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Optional<ApiKey> key = apiKeyService.authenticate(presented);
                key.ifPresent(k -> authenticate(k, request));
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private static void authenticate(ApiKey key, HttpServletRequest request) {
        UserPrincipal principal = new UserPrincipal(
                key.getCreatedBy(), key.getTenantId(),
                "api-key:" + key.getKeyPrefix(), null, key.getRole(), true);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + key.getRole().name())));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
        TenantContext.set(key.getTenantId());
    }
}
