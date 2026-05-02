package com.invoicebuilder.tenant;

import com.invoicebuilder.common.dto.ApiResponse;
import com.invoicebuilder.tenant.dto.TenantResponse;
import com.invoicebuilder.tenant.dto.TenantUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant")
@Tag(name = "Tenant", description = "Read and update the current tenant's settings")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    @Operation(summary = "Get the current tenant")
    public ApiResponse<TenantResponse> getCurrent() {
        return ApiResponse.of(tenantService.getCurrent());
    }

    @PutMapping
    @Operation(summary = "Update the current tenant's settings")
    public ApiResponse<TenantResponse> update(@Valid @RequestBody TenantUpdateRequest request) {
        return ApiResponse.of(tenantService.update(request));
    }
}
