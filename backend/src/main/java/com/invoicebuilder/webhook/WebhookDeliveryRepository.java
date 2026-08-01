package com.invoicebuilder.webhook;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    List<WebhookDelivery> findByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
            WebhookDelivery.Status status, OffsetDateTime now, Limit limit);

    List<WebhookDelivery> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Limit limit);
}
