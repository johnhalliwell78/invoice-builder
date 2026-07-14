package com.invoicebuilder.common.ratelimit;

import com.invoicebuilder.auth.UserPrincipal;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Per-user API throttle (default 100 requests/minute). Runs after the JWT
 * filter so the authenticated principal is available; anonymous and non-API
 * requests are skipped (login already has its own per-email limiter).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimitService rateLimitService;
    private final AppProperties appProperties;

    public RateLimitFilter(RateLimitService rateLimitService, AppProperties appProperties) {
        this.rateLimitService = rateLimitService;
        this.appProperties = appProperties;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            chain.doFilter(request, response);
            return;
        }
        String key = "rl:api:" + principal.userId();
        int limit = appProperties.rateLimit().apiRequestsPerMinute();
        if (!rateLimitService.tryAcquire(key, limit, WINDOW)) {
            writeTooManyRequests(response, request.getRequestURI());
            return;
        }
        chain.doFilter(request, response);
    }

    private static UserPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal up ? up : null;
    }

    private static void writeTooManyRequests(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"title":"Too Many Requests","status":429,"code":"%s","instance":"%s"}"""
                .formatted(ErrorCode.RATE_LIMIT_EXCEEDED.code(), path));
    }
}
