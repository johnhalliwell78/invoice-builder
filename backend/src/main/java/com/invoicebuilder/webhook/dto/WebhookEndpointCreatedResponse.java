package com.invoicebuilder.webhook.dto;

/**
 * Creation response. Unlike an API key the signing secret stays retrievable:
 * it authenticates <em>us to the receiver</em>, and they need it to verify
 * every delivery.
 */
public record WebhookEndpointCreatedResponse(
        WebhookEndpointResponse endpoint,
        String secret
) {
}
