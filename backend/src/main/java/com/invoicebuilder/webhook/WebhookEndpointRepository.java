package com.invoicebuilder.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    List<WebhookEndpoint> findByTenantIdAndActiveTrue(UUID tenantId);

    List<WebhookEndpoint> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<WebhookEndpoint> findByIdAndTenantId(UUID id, UUID tenantId);
}
