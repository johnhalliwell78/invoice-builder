package com.invoicebuilder.webhook;

import com.invoicebuilder.common.exception.AppException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Webhook URLs are supplied by tenants, so the sender is a request forgery
 * primitive unless the destination is constrained (SSRF, CWE-918).
 */
class WebhookUrlValidatorTest {

    @Test
    void ordinaryPublicHttpsEndpointsAreAccepted() {
        assertThat(WebhookUrlValidator.isAllowed("https://example.com/hooks/invoice")).isTrue();
        assertThat(WebhookUrlValidator.isAllowed("https://hooks.example.co.uk:8443/x")).isTrue();
    }

    @Test
    void loopbackAndLinkLocalAddressesAreRefused() {
        // 169.254.169.254 is the cloud metadata endpoint — the classic SSRF
        // target for stealing instance credentials.
        assertThat(WebhookUrlValidator.isAllowed("http://169.254.169.254/latest/meta-data/")).isFalse();
        assertThat(WebhookUrlValidator.isAllowed("http://127.0.0.1:8080/actuator")).isFalse();
        assertThat(WebhookUrlValidator.isAllowed("https://localhost/internal")).isFalse();
        assertThat(WebhookUrlValidator.isAllowed("http://[::1]/")).isFalse();
    }

    @Test
    void privateNetworkRangesAreRefused() {
        assertThat(WebhookUrlValidator.isAllowed("http://10.0.0.5/hook")).isFalse();
        assertThat(WebhookUrlValidator.isAllowed("http://192.168.1.10/hook")).isFalse();
        assertThat(WebhookUrlValidator.isAllowed("http://172.16.0.1/hook")).isFalse();
    }

    @Test
    void onlyHttpSchemesAreAllowed() {
        assertThat(WebhookUrlValidator.isAllowed("file:///etc/passwd")).isFalse();
        assertThat(WebhookUrlValidator.isAllowed("gopher://example.com/")).isFalse();
        assertThat(WebhookUrlValidator.isAllowed("ftp://example.com/")).isFalse();
    }

    @Test
    void malformedInputIsRefusedRatherThanGuessedAt() {
        assertThat(WebhookUrlValidator.isAllowed("not a url")).isFalse();
        assertThat(WebhookUrlValidator.isAllowed("")).isFalse();
        assertThat(WebhookUrlValidator.isAllowed(null)).isFalse();
    }

    @Test
    void requireAllowedThrowsSoTheRejectionSurfacesToTheOperator() {
        assertThatThrownBy(() -> WebhookUrlValidator.requireAllowed("http://127.0.0.1/x"))
                .isInstanceOf(AppException.class);
    }
}
