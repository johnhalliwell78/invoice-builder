package com.invoicebuilder.apikey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /** Tenant-less by necessity: the key itself is what identifies the tenant. */
    Optional<ApiKey> findByKeyHash(String keyHash);

    Optional<ApiKey> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ApiKey> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
