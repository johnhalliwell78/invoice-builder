package com.invoicebuilder.webhook;

import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Guards which destinations a tenant may point a webhook at.
 *
 * <p>Without this the sender is a server-side request forgery primitive
 * (CWE-918): a tenant could register {@code http://169.254.169.254/} and have
 * us fetch cloud instance credentials on their behalf, or probe internal
 * services unreachable from outside.</p>
 *
 * <p>Two layers, because neither alone is honest:</p>
 * <ul>
 *   <li>{@link #isAllowed} runs at registration. It is deliberately
 *       <em>offline</em> — scheme, host shape, and literal-IP ranges — so a
 *       DNS outage cannot start rejecting legitimate endpoints, and so it is
 *       testable without a network.</li>
 *   <li>{@link #isSafeDestination} runs in the sender, immediately before
 *       connecting, and refuses any host that <em>resolves</em> to an
 *       internal address. That is where a hostname disguising an internal
 *       target gets caught.</li>
 * </ul>
 *
 * <p>Even together these do not close DNS rebinding, where the answer changes
 * between check and connect. Production deployments should egress-filter the
 * sender; that residual gap is documented rather than papered over.</p>
 */
public final class WebhookUrlValidator {

    private static final Logger log = LoggerFactory.getLogger(WebhookUrlValidator.class);
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Set<String> BLOCKED_HOSTS = Set.of("localhost", "localhost.localdomain");

    private WebhookUrlValidator() {
    }

    /** Offline registration check: scheme, host shape, and literal IP ranges. */
    public static boolean isAllowed(String url) {
        URI uri = parse(url);
        if (uri == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (BLOCKED_HOSTS.contains(host)) {
            return false;
        }
        // A literal address can be judged with no network at all; a hostname
        // is left to the send-time check.
        InetAddress literal = parseLiteral(host);
        return literal == null || !isInternal(literal);
    }

    public static void requireAllowed(String url) {
        if (!isAllowed(url)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Webhook URL must be a public http(s) address");
        }
    }

    /** Send-time check: resolves the host and refuses internal targets. */
    public static boolean isSafeDestination(String url) {
        if (!isAllowed(url)) {
            return false;
        }
        URI uri = parse(url);
        if (uri == null) {
            return false;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (isInternal(address)) {
                    log.warn("Refusing webhook to {} — resolves to internal address {}",
                            uri.getHost(), address.getHostAddress());
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            log.debug("Webhook host {} does not resolve", uri.getHost());
            return false;
        }
    }

    private static URI parse(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme) || uri.getHost() == null || uri.getHost().isBlank()) {
            return null;
        }
        return uri;
    }

    /** Parses an IP literal without ever performing a lookup. */
    private static InetAddress parseLiteral(String host) {
        String candidate = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
        if (!candidate.matches("[0-9.]+") && !candidate.contains(":")) {
            return null;
        }
        try {
            // Safe: a literal never triggers a name-service lookup.
            return InetAddress.getByName(candidate);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static boolean isInternal(InetAddress address) {
        return address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress();
    }
}
