package com.invoicebuilder.recurring;

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
public interface RecurringInvoiceRepository extends JpaRepository<RecurringInvoice, UUID> {

    Optional<RecurringInvoice> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<RecurringInvoice> findByTenantId(UUID tenantId, Pageable pageable);

    List<RecurringInvoice> findByTenantIdAndActiveTrueAndNextRunLessThanEqual(UUID tenantId, LocalDate date);

    @Query("select distinct r.tenantId from RecurringInvoice r where r.active = true and r.nextRun <= :date")
    List<UUID> findTenantIdsWithDueSchedules(@Param("date") LocalDate date);
}
