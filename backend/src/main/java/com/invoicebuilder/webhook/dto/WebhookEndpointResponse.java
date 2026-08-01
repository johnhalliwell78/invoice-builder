package com.invoicebuilder.webhook.dto;

import com.invoicebuilder.notification.NotificationType;
import com.invoicebuilder.webhook.WebhookEndpoint;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record WebhookEndpointResponse(
        UUID id,
        String url,
        List<NotificationType> eventTypes,
        boolean active,
        OffsetDateTime createdAt
) {

    public static WebhookEndpointResponse from(WebhookEndpoint endpoint) {
        return new WebhookEndpointResponse(endpoint.getId(), endpoint.getUrl(),
                endpoint.getEventTypes(), endpoint.isActive(), endpoint.getCreatedAt());
    }
}
