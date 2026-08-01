package com.invoicebuilder.webhook;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSenderBackoffTest {

    @Test
    void retriesBackOffExponentiallyRatherThanHammering() {
        assertThat(WebhookSender.backoff(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(WebhookSender.backoff(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(WebhookSender.backoff(3)).isEqualTo(Duration.ofMinutes(4));
        assertThat(WebhookSender.backoff(4)).isEqualTo(Duration.ofMinutes(8));
    }

    @Test
    void theDelayIsCappedSoARetryNeverDriftsIntoDays() {
        assertThat(WebhookSender.backoff(6)).isEqualTo(Duration.ofMinutes(32));
        assertThat(WebhookSender.backoff(20)).isEqualTo(Duration.ofMinutes(32));
    }
}
