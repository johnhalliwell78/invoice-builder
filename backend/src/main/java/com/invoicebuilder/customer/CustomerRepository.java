package com.invoicebuilder.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    @Query("""
            select c from Customer c
            where c.tenantId = :tenantId
              and c.deletedAt is null
              and (:q is null or :q = ''
                   or lower(c.name) like lower(concat('%', :q, '%'))
                   or lower(c.email) like lower(concat('%', :q, '%'))
                   or lower(coalesce(c.company, '')) like lower(concat('%', :q, '%')))
            """)
    Page<Customer> search(@Param("tenantId") UUID tenantId,
                          @Param("q") String query,
                          Pageable pageable);
}
