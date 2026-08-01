package com.invoicebuilder.webhook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureTest {

    @Test
    void signatureCarriesTheTimestampSoReceiversCanRejectReplays() {
        String header = WebhookSignature.sign("{\"a\":1}", "whsec_test", 1785000000L);

        assertThat(header).startsWith("t=1785000000,v1=");
    }

    @Test
    void theSameInputAlwaysProducesTheSameSignature() {
        String first = WebhookSignature.sign("{\"a\":1}", "s", 1L);
        String second = WebhookSignature.sign("{\"a\":1}", "s", 1L);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void tamperingWithPayloadTimestampOrSecretChangesTheSignature() {
        String base = WebhookSignature.sign("{\"a\":1}", "s", 1L);

        assertThat(WebhookSignature.sign("{\"a\":2}", "s", 1L)).isNotEqualTo(base);
        assertThat(WebhookSignature.sign("{\"a\":1}", "s", 2L)).isNotEqualTo(base);
        // A receiver holding a different secret must not validate our body.
        assertThat(WebhookSignature.sign("{\"a\":1}", "other", 1L)).isNotEqualTo(base);
    }
}
