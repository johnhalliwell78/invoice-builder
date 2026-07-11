package com.invoicebuilder.dashboard;

import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.dashboard.dto.DashboardResponse;
import com.invoicebuilder.invoice.InvoiceRepository;
import com.invoicebuilder.invoice.InvoiceStatus;
import com.invoicebuilder.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    // Fixed "now": 2026-07-11 → the 12-month window is 2025-08 … 2026-07.
    private static final Instant NOW = Instant.parse("2026-07-11T12:00:00Z");

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CustomerRepository customerRepository;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(invoiceRepository, customerRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        TenantContext.set(TENANT_ID);

        lenient().when(invoiceRepository.sumOpenByCurrencyAndStatus(eq(TENANT_ID), anyList()))
                .thenReturn(List.of());
        lenient().when(invoiceRepository.sumPaidSinceByCurrency(eq(TENANT_ID), any()))
                .thenReturn(List.of());
        lenient().when(invoiceRepository.countByStatus(TENANT_ID)).thenReturn(List.of());
        lenient().when(invoiceRepository.revenueByMonth(eq(TENANT_ID), any())).thenReturn(List.of());
        lenient().when(customerRepository.newCustomersByMonth(eq(TENANT_ID), any())).thenReturn(List.of());
        lenient().when(invoiceRepository.recentWithCustomerName(eq(TENANT_ID), any()))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void combinesOpenSumsIntoOutstandingAndOverdueTilesPerCurrency() {
        when(invoiceRepository.sumOpenByCurrencyAndStatus(eq(TENANT_ID), anyList())).thenReturn(List.of(
                new Object[]{"EUR", InvoiceStatus.SENT, new BigDecimal("100.00")},
                new Object[]{"EUR", InvoiceStatus.OVERDUE, new BigDecimal("50.00")},
                new Object[]{"USD", InvoiceStatus.VIEWED, new BigDecimal("30.00")}));
        when(invoiceRepository.sumPaidSinceByCurrency(eq(TENANT_ID), any())).thenReturn(
                List.<Object[]>of(new Object[]{"EUR", new BigDecimal("200.00")}));

        DashboardResponse response = service.getDashboard();

        assertThat(response.tiles()).hasSize(2);
        DashboardResponse.CurrencyTile eur = response.tiles().stream()
                .filter(tile -> tile.currency().equals("EUR")).findFirst().orElseThrow();
        assertThat(eur.outstanding()).isEqualByComparingTo("150.00");
        assertThat(eur.overdue()).isEqualByComparingTo("50.00");
        assertThat(eur.paidThisMonth()).isEqualByComparingTo("200.00");
        DashboardResponse.CurrencyTile usd = response.tiles().stream()
                .filter(tile -> tile.currency().equals("USD")).findFirst().orElseThrow();
        assertThat(usd.outstanding()).isEqualByComparingTo("30.00");
        assertThat(usd.paidThisMonth()).isEqualByComparingTo("0");
    }

    @Test
    void zeroFillsRevenueAcrossTwelveMonthsPerCurrency() {
        when(invoiceRepository.revenueByMonth(eq(TENANT_ID), any())).thenReturn(List.of(
                new Object[]{"2026-05", "EUR", new BigDecimal("300.00")},
                new Object[]{"2026-07", "EUR", new BigDecimal("120.00")}));

        DashboardResponse response = service.getDashboard();

        List<DashboardResponse.MonthAmount> eur = response.revenueByMonth().stream()
                .filter(entry -> entry.currency().equals("EUR")).toList();
        assertThat(eur).hasSize(12);
        assertThat(eur.get(0).month()).isEqualTo("2025-08");
        assertThat(eur.get(11).month()).isEqualTo("2026-07");
        assertThat(eur.get(11).total()).isEqualByComparingTo("120.00");
        assertThat(eur.get(9).month()).isEqualTo("2026-05");
        assertThat(eur.get(9).total()).isEqualByComparingTo("300.00");
        assertThat(eur.get(0).total()).isEqualByComparingTo("0");
    }

    @Test
    void zeroFillsCustomerGrowthForAllTwelveMonths() {
        when(customerRepository.newCustomersByMonth(eq(TENANT_ID), any())).thenReturn(
                List.<Object[]>of(new Object[]{"2026-06", 4L}));

        DashboardResponse response = service.getDashboard();

        assertThat(response.customersByMonth()).hasSize(12);
        assertThat(response.customersByMonth().get(10).month()).isEqualTo("2026-06");
        assertThat(response.customersByMonth().get(10).count()).isEqualTo(4);
        assertThat(response.customersByMonth().get(11).count()).isZero();
    }

    @Test
    void mapsStatusCountsAndRecentInvoices() {
        when(invoiceRepository.countByStatus(TENANT_ID)).thenReturn(List.of(
                new Object[]{InvoiceStatus.DRAFT, 3L},
                new Object[]{InvoiceStatus.PAID, 7L}));
        UUID invoiceId = UUID.randomUUID();
        OffsetDateTime updated = OffsetDateTime.parse("2026-07-10T09:00:00Z");
        when(invoiceRepository.recentWithCustomerName(eq(TENANT_ID), any())).thenReturn(
                List.<Object[]>of(new Object[]{invoiceId, "INV-2026-0009", InvoiceStatus.SENT,
                        new BigDecimal("99.00"), "EUR", "Widget Co", updated}));

        DashboardResponse response = service.getDashboard();

        assertThat(response.statusCounts())
                .containsEntry(InvoiceStatus.DRAFT, 3L)
                .containsEntry(InvoiceStatus.PAID, 7L);
        assertThat(response.recentInvoices()).hasSize(1);
        assertThat(response.recentInvoices().get(0).customerName()).isEqualTo("Widget Co");
        assertThat(response.recentInvoices().get(0).invoiceNumber()).isEqualTo("INV-2026-0009");
    }
}
