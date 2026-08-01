package com.invoicebuilder.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Delivers queued webhooks with signed bodies and exponential backoff.
 *
 * <p>Runs on a schedule rather than inline so a slow or dead receiver cannot
 * hold open the transaction that produced the event.</p>
 */
@Component
public class WebhookSender {

    private static final Logger log = LoggerFactory.getLogger(WebhookSender.class);
    private static final int MAX_ATTEMPTS = 6;
    private static final int BATCH = 50;

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final RestClient restClient;
    private final Clock clock;

    public WebhookSender(WebhookDeliveryRepository deliveryRepository,
                         WebhookEndpointRepository endpointRepository,
                         RestClient.Builder restClientBuilder,
                         Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.endpointRepository = endpointRepository;
        this.restClient = restClientBuilder.build();
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void dispatchDue() {
        List<WebhookDelivery> due = deliveryRepository
                .findByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
                        WebhookDelivery.Status.PENDING, OffsetDateTime.now(clock), Limit.of(BATCH));
        for (WebhookDelivery delivery : due) {
            try {
                attempt(delivery);
            } catch (RuntimeException e) {
                // One bad delivery must not stop the rest of the batch.
                log.error("Webhook delivery {} blew up", delivery.getId(), e);
                fail(delivery, e.getClass().getSimpleName());
            }
        }
    }

    private void attempt(WebhookDelivery delivery) {
        Optional<WebhookEndpoint> endpoint = endpointRepository.findById(delivery.getEndpointId());
        if (endpoint.isEmpty() || !endpoint.get().isActive()) {
            // Endpoint deleted or paused since queueing — nothing to deliver to.
            delivery.setStatus(WebhookDelivery.Status.FAILED);
            delivery.setLastError("endpoint unavailable");
            return;
        }
        String url = endpoint.get().getUrl();
        // Re-checked immediately before connecting: a hostname could resolve
        // somewhere internal even though it looked fine at registration.
        if (!WebhookUrlValidator.isSafeDestination(url)) {
            delivery.setStatus(WebhookDelivery.Status.FAILED);
            delivery.setLastError("destination not permitted");
            return;
        }

        long timestamp = OffsetDateTime.now(clock).toEpochSecond();
        String signature = WebhookSignature.sign(delivery.getPayload(), endpoint.get().getSecret(), timestamp);
        delivery.setAttempts(delivery.getAttempts() + 1);

        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(WebhookSignature.HEADER, signature)
                    .header("X-InvoiceBuilder-Event", delivery.getEventType())
                    .body(delivery.getPayload())
                    .retrieve()
                    .toBodilessEntity();
            delivery.setStatus(WebhookDelivery.Status.DELIVERED);
            delivery.setDeliveredAt(OffsetDateTime.now(clock));
            delivery.setLastError(null);
        } catch (RuntimeException e) {
            fail(delivery, truncate(e.getMessage()));
        }
    }

    private void fail(WebhookDelivery delivery, String error) {
        delivery.setLastError(error);
        if (delivery.getAttempts() >= MAX_ATTEMPTS) {
            delivery.setStatus(WebhookDelivery.Status.FAILED);
            log.warn("Webhook delivery {} exhausted {} attempts", delivery.getId(), MAX_ATTEMPTS);
            return;
        }
        delivery.setNextAttemptAt(OffsetDateTime.now(clock).plus(backoff(delivery.getAttempts())));
    }

    /** 1, 2, 4, 8… minutes — quick enough to ride out a blip, slow enough not to hammer. */
    static Duration backoff(int attempts) {
        return Duration.ofMinutes(1L << Math.min(attempts - 1, 5));
    }

    private static String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
