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
 * Two throttles. Authenticated traffic is limited per user (default 100/min).
 * Anonymous traffic under {@code /api/v1/public/**} is limited per client IP,
 * because those endpoints need no credentials yet can be expensive — starting
 * a Stripe Checkout session performs an outbound API call and creates a real
 * Session on the operator's account.
 *
 * <p>The Stripe webhook is deliberately exempt: throttling it would drop
 * deliveries that book money, and it authenticates itself by signature.</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String PUBLIC_PREFIX = "/api/v1/public/";
    private static final String WEBHOOK_PATH = "/api/v1/public/stripe/webhook";

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
        String key;
        int limit;
        if (principal != null) {
            key = "rl:api:" + principal.userId();
            limit = appProperties.rateLimit().apiRequestsPerMinute();
        } else if (isThrottledPublicPath(request)) {
            key = "rl:public:" + clientIp(request);
            limit = appProperties.rateLimit().publicRequestsPerMinute();
        } else {
            chain.doFilter(request, response);
            return;
        }
        if (!rateLimitService.tryAcquire(key, limit, WINDOW)) {
            writeTooManyRequests(response, request.getRequestURI());
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isThrottledPublicPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith(PUBLIC_PREFIX) && !path.equals(WEBHOOK_PATH);
    }

    /** Honours X-Forwarded-For's first hop when running behind a proxy. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
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
