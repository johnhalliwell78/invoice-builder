package com.invoicebuilder.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("""
            select p from Product p
            where p.tenantId = :tenantId
              and (:activeOnly = false or p.active = true)
              and (:q is null or :q = ''
                   or lower(p.name) like lower(concat('%', :q, '%'))
                   or lower(coalesce(p.category, '')) like lower(concat('%', :q, '%')))
            """)
    Page<Product> search(@Param("tenantId") UUID tenantId,
                         @Param("q") String query,
                         @Param("activeOnly") boolean activeOnly,
                         Pageable pageable);
}
