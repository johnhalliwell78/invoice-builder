package com.invoicebuilder.notification;

import java.util.UUID;

/**
 * Domain event published by services when something notification-worthy
 * happens. A listener persists (and, once real-time lands, broadcasts) it after
 * the originating transaction commits. {@code userId} is the recipient; a null
 * recipient is silently ignored.
 *
 * @param subject short human label folded into the message (e.g. the invoice
 *                number or customer name)
 */
public record NotificationEvent(
        UUID tenantId,
        UUID userId,
        NotificationType type,
        String referenceType,
        UUID referenceId,
        String subject
) {
}
