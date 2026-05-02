package com.invoicebuilder.invoice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Invoice> findByPublicToken(String publicToken);

    @Query("""
            select i from Invoice i
            where i.tenantId = :tenantId
              and (:status is null or i.status = :status)
              and (:customerId is null or i.customerId = :customerId)
              and (:fromDate is null or i.issueDate >= :fromDate)
              and (:toDate is null or i.issueDate <= :toDate)
            """)
    Page<Invoice> search(@Param("tenantId") UUID tenantId,
                         @Param("status") InvoiceStatus status,
                         @Param("customerId") UUID customerId,
                         @Param("fromDate") LocalDate fromDate,
                         @Param("toDate") LocalDate toDate,
                         Pageable pageable);

    @Query("select i.id from Invoice i where i.tenantId = :tenantId and i.status in :statuses and i.dueDate < :today")
    List<UUID> findOverdueIds(@Param("tenantId") UUID tenantId,
                              @Param("statuses") List<InvoiceStatus> statuses,
                              @Param("today") LocalDate today);
}
