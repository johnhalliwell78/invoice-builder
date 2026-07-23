package com.invoicebuilder.invoice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Row-locked load for read-modify-write flows (payments, estimate
     * conversion): serializes concurrent mutations of the same invoice so
     * balance checks and one-shot guards cannot race.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id and i.tenantId = :tenantId")
    Optional<Invoice> findByIdAndTenantIdForUpdate(@Param("id") UUID id,
                                                   @Param("tenantId") UUID tenantId);

    Optional<Invoice> findByPublicToken(String publicToken);

    @Query("""
            select i from Invoice i
            where i.tenantId = :tenantId
              and i.docType = :docType
              and (:status is null or i.status = :status)
              and (:customerId is null or i.customerId = :customerId)
              and (:fromDate is null or i.issueDate >= :fromDate)
              and (:toDate is null or i.issueDate <= :toDate)
            """)
    Page<Invoice> search(@Param("tenantId") UUID tenantId,
                         @Param("docType") DocType docType,
                         @Param("status") InvoiceStatus status,
                         @Param("customerId") UUID customerId,
                         @Param("fromDate") LocalDate fromDate,
                         @Param("toDate") LocalDate toDate,
                         Pageable pageable);

    @Query("select i.id from Invoice i where i.tenantId = :tenantId and i.status in :statuses and i.dueDate < :today and i.docType = com.invoicebuilder.invoice.DocType.INVOICE")
    List<UUID> findOverdueIds(@Param("tenantId") UUID tenantId,
                              @Param("statuses") List<InvoiceStatus> statuses,
                              @Param("today") LocalDate today);

    @Query("select distinct i.tenantId from Invoice i where i.status in :statuses and i.dueDate < :today and i.docType = com.invoicebuilder.invoice.DocType.INVOICE")
    List<UUID> findTenantIdsWithOverdueCandidates(@Param("statuses") List<InvoiceStatus> statuses,
                                                  @Param("today") LocalDate today);

    // ---------- dashboard aggregates ----------

    @Query("""
            select i.currency, i.status, sum(i.total) from Invoice i
            where i.tenantId = :tenantId and i.status in :statuses
              and i.docType = com.invoicebuilder.invoice.DocType.INVOICE
            group by i.currency, i.status
            """)
    List<Object[]> sumOpenByCurrencyAndStatus(@Param("tenantId") UUID tenantId,
                                              @Param("statuses") List<InvoiceStatus> statuses);

    @Query("""
            select i.currency, sum(i.total) from Invoice i
            where i.tenantId = :tenantId and i.status = com.invoicebuilder.invoice.InvoiceStatus.PAID
              and i.paidAt >= :since
            group by i.currency
            """)
    List<Object[]> sumPaidSinceByCurrency(@Param("tenantId") UUID tenantId,
                                          @Param("since") OffsetDateTime since);

    @Query("select i.status, count(i) from Invoice i where i.tenantId = :tenantId and i.docType = com.invoicebuilder.invoice.DocType.INVOICE group by i.status")
    List<Object[]> countByStatus(@Param("tenantId") UUID tenantId);

    @Query(value = """
            select to_char(i.paid_at, 'YYYY-MM') as month, i.currency, sum(i.total)
            from invoice i
            where i.tenant_id = :tenantId and i.status = 'PAID' and i.doc_type = 'INVOICE' and i.paid_at >= :since
            group by 1, 2
            order by 1
            """, nativeQuery = true)
    List<Object[]> revenueByMonth(@Param("tenantId") UUID tenantId,
                                  @Param("since") OffsetDateTime since);

    @Query("""
            select i.id, i.invoiceNumber, i.status, i.total, i.currency, c.name, i.updatedAt
            from Invoice i, Customer c
            where i.tenantId = :tenantId and c.id = i.customerId
              and i.docType = com.invoicebuilder.invoice.DocType.INVOICE
            order by i.updatedAt desc
            """)
    List<Object[]> recentWithCustomerName(@Param("tenantId") UUID tenantId, Pageable pageable);
}
