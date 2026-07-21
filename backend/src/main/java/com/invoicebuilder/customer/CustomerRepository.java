package com.invoicebuilder.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    /** Batch name lookup for list views; includes soft-deleted customers on purpose. */
    List<Customer> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

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

    @Query(value = """
            select to_char(c.created_at, 'YYYY-MM') as month, count(*)
            from customer c
            where c.tenant_id = :tenantId and c.created_at >= :since and c.deleted_at is null
            group by 1
            order by 1
            """, nativeQuery = true)
    List<Object[]> newCustomersByMonth(@Param("tenantId") UUID tenantId,
                                       @Param("since") java.time.OffsetDateTime since);
}
