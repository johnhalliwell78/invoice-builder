package com.invoicebuilder.dashboard;

import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.dashboard.dto.DashboardResponse;
import com.invoicebuilder.dashboard.dto.DashboardResponse.CurrencyTile;
import com.invoicebuilder.dashboard.dto.DashboardResponse.MonthAmount;
import com.invoicebuilder.dashboard.dto.DashboardResponse.MonthCount;
import com.invoicebuilder.dashboard.dto.DashboardResponse.RecentInvoice;
import com.invoicebuilder.invoice.InvoiceRepository;
import com.invoicebuilder.invoice.InvoiceStatus;
import com.invoicebuilder.tenant.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class DashboardService {

    private static final int MONTHS = 12;
    private static final List<InvoiceStatus> OPEN_STATUSES =
            List.of(InvoiceStatus.SENT, InvoiceStatus.VIEWED, InvoiceStatus.OVERDUE);

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final Clock clock;

    public DashboardService(InvoiceRepository invoiceRepository,
                            CustomerRepository customerRepository,
                            Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        UUID tenantId = TenantContext.require();
        YearMonth currentMonth = YearMonth.from(OffsetDateTime.now(clock));
        OffsetDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime windowStart = currentMonth.minusMonths(MONTHS - 1L)
                .atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        List<String> months = lastMonths(currentMonth, MONTHS);

        return new DashboardResponse(
                buildTiles(tenantId, startOfMonth),
                buildStatusCounts(tenantId),
                buildRevenueByMonth(tenantId, windowStart, months),
                buildCustomersByMonth(tenantId, windowStart, months),
                buildRecentInvoices(tenantId));
    }

    private List<CurrencyTile> buildTiles(UUID tenantId, OffsetDateTime startOfMonth) {
        Map<String, BigDecimal> outstanding = new TreeMap<>();
        Map<String, BigDecimal> overdue = new HashMap<>();
        for (Object[] row : invoiceRepository.sumOpenByCurrencyAndStatus(tenantId, OPEN_STATUSES)) {
            String currency = (String) row[0];
            InvoiceStatus status = (InvoiceStatus) row[1];
            BigDecimal sum = (BigDecimal) row[2];
            outstanding.merge(currency, sum, BigDecimal::add);
            if (status == InvoiceStatus.OVERDUE) {
                overdue.merge(currency, sum, BigDecimal::add);
            }
        }
        Map<String, BigDecimal> paid = new HashMap<>();
        for (Object[] row : invoiceRepository.sumPaidSinceByCurrency(tenantId, startOfMonth)) {
            paid.put((String) row[0], (BigDecimal) row[1]);
        }

        Map<String, CurrencyTile> tiles = new TreeMap<>();
        outstanding.forEach((currency, sum) -> tiles.put(currency, new CurrencyTile(currency, sum,
                overdue.getOrDefault(currency, BigDecimal.ZERO),
                paid.getOrDefault(currency, BigDecimal.ZERO))));
        paid.forEach((currency, sum) -> tiles.computeIfAbsent(currency,
                c -> new CurrencyTile(c, BigDecimal.ZERO, BigDecimal.ZERO, sum)));
        return List.copyOf(tiles.values());
    }

    private Map<InvoiceStatus, Long> buildStatusCounts(UUID tenantId) {
        Map<InvoiceStatus, Long> counts = new EnumMap<>(InvoiceStatus.class);
        for (Object[] row : invoiceRepository.countByStatus(tenantId)) {
            counts.put((InvoiceStatus) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private List<MonthAmount> buildRevenueByMonth(UUID tenantId, OffsetDateTime windowStart,
                                                  List<String> months) {
        // currency → (month → total)
        Map<String, Map<String, BigDecimal>> byCurrency = new TreeMap<>();
        for (Object[] row : invoiceRepository.revenueByMonth(tenantId, windowStart)) {
            byCurrency.computeIfAbsent((String) row[1], c -> new LinkedHashMap<>())
                    .put((String) row[0], (BigDecimal) row[2]);
        }
        List<MonthAmount> series = new ArrayList<>();
        byCurrency.forEach((currency, perMonth) -> {
            for (String month : months) {
                series.add(new MonthAmount(month, currency,
                        perMonth.getOrDefault(month, BigDecimal.ZERO)));
            }
        });
        return series;
    }

    private List<MonthCount> buildCustomersByMonth(UUID tenantId, OffsetDateTime windowStart,
                                                   List<String> months) {
        Map<String, Long> perMonth = new HashMap<>();
        for (Object[] row : customerRepository.newCustomersByMonth(tenantId, windowStart)) {
            perMonth.put((String) row[0], ((Number) row[1]).longValue());
        }
        return months.stream()
                .map(month -> new MonthCount(month, perMonth.getOrDefault(month, 0L)))
                .toList();
    }

    private List<RecentInvoice> buildRecentInvoices(UUID tenantId) {
        return invoiceRepository.recentWithCustomerName(tenantId, PageRequest.of(0, 8)).stream()
                .map(row -> new RecentInvoice(
                        (UUID) row[0],
                        (String) row[1],
                        (InvoiceStatus) row[2],
                        (BigDecimal) row[3],
                        (String) row[4],
                        (String) row[5],
                        (OffsetDateTime) row[6]))
                .toList();
    }

    /** Oldest-first list of the last {@code n} months as {@code YYYY-MM}. */
    static List<String> lastMonths(YearMonth end, int n) {
        List<String> months = new ArrayList<>(n);
        for (int i = n - 1; i >= 0; i--) {
            months.add(end.minusMonths(i).toString());
        }
        return months;
    }
}
