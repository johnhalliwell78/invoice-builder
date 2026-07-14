package com.invoicebuilder.audit;

import com.invoicebuilder.audit.dto.AuditLogResponse;
import com.invoicebuilder.common.dto.ApiResponse;
import com.invoicebuilder.common.dto.PageResponse;
import com.invoicebuilder.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit", description = "Audit trail (owner/admin)")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @Operation(summary = "List audit-log entries for the tenant (paginated)")
    public ApiResponse<PageResponse<AuditLogResponse>> list(
            @PageableDefault(size = 30, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.of(PageResponse.of(
                auditService.forTenant(TenantContext.require(), pageable), AuditLogResponse::from));
    }

    @GetMapping("/entity/{type}/{id}")
    @Operation(summary = "Audit trail for a specific entity")
    public ApiResponse<List<AuditLogResponse>> forEntity(@PathVariable String type,
                                                         @PathVariable UUID id) {
        return ApiResponse.of(auditService.forEntity(TenantContext.require(), type, id).stream()
                .map(AuditLogResponse::from).toList());
    }
}
