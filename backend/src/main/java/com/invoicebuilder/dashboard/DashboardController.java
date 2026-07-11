package com.invoicebuilder.dashboard;

import com.invoicebuilder.common.dto.ApiResponse;
import com.invoicebuilder.dashboard.dto.DashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Tenant-level KPIs, charts, and recent activity")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "Dashboard aggregates: tiles, status counts, 12-month series, recent invoices")
    public ApiResponse<DashboardResponse> getDashboard() {
        return ApiResponse.of(dashboardService.getDashboard());
    }
}
