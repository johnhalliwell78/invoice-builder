package com.invoicebuilder.invoice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OverdueSweeperTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 11);

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceService invoiceService;

    private OverdueSweeper sweeper() {
        return new OverdueSweeper(invoiceRepository, invoiceService,
                Clock.fixed(Instant.parse("2026-07-11T03:15:00Z"), ZoneOffset.UTC));
    }

    @Test
    void sweepMarksOverdueForEveryTenantWithCandidates() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        when(invoiceRepository.findTenantIdsWithOverdueCandidates(anyList(), eq(TODAY)))
                .thenReturn(List.of(tenantA, tenantB));
        when(invoiceService.markOverdueForTenant(any(UUID.class), eq(TODAY))).thenReturn(1);

        sweeper().sweep();

        verify(invoiceService).markOverdueForTenant(tenantA, TODAY);
        verify(invoiceService).markOverdueForTenant(tenantB, TODAY);
    }

    @Test
    void sweepContinuesWhenOneTenantFails() {
        UUID failing = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        when(invoiceRepository.findTenantIdsWithOverdueCandidates(anyList(), eq(TODAY)))
                .thenReturn(List.of(failing, healthy));
        when(invoiceService.markOverdueForTenant(failing, TODAY))
                .thenThrow(new IllegalStateException("boom"));
        when(invoiceService.markOverdueForTenant(healthy, TODAY)).thenReturn(2);

        sweeper().sweep();

        verify(invoiceService).markOverdueForTenant(healthy, TODAY);
    }
}
