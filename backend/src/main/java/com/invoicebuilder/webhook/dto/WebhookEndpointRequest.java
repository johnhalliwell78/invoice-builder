package com.invoicebuilder.webhook.dto;

import com.invoicebuilder.notification.NotificationType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WebhookEndpointRequest(
        @NotBlank @Size(max = 2000) String url,

        @NotEmpty(message = "Subscribe to at least one event")
        List<NotificationType> eventTypes
) {
}
