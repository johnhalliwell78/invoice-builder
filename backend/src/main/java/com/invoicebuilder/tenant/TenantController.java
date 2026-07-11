package com.invoicebuilder.tenant;

import com.invoicebuilder.common.dto.ApiResponse;
import com.invoicebuilder.tenant.dto.TenantResponse;
import com.invoicebuilder.tenant.dto.TenantUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/tenant")
@Tag(name = "Tenant", description = "Read and update the current tenant's settings")
public class TenantController {

    private final TenantService tenantService;
    private final LogoStorage logoStorage;

    public TenantController(TenantService tenantService, LogoStorage logoStorage) {
        this.tenantService = tenantService;
        this.logoStorage = logoStorage;
    }

    @GetMapping
    @Operation(summary = "Get the current tenant")
    public ApiResponse<TenantResponse> getCurrent() {
        return ApiResponse.of(tenantService.getCurrent());
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @Operation(summary = "Update the current tenant's settings (owner/admin)")
    public ApiResponse<TenantResponse> update(@Valid @RequestBody TenantUpdateRequest request) {
        return ApiResponse.of(tenantService.update(request));
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @Operation(summary = "Upload the tenant logo (PNG or JPEG, max 2 MB; owner/admin)")
    public ApiResponse<TenantResponse> uploadLogo(@RequestParam("file") MultipartFile file) throws IOException {
        Tenant tenant = tenantService.loadCurrent();
        String path = logoStorage.save(tenant, file.getBytes(), file.getContentType());
        return ApiResponse.of(tenantService.setLogoPath(path));
    }

    @GetMapping("/logo")
    @Operation(summary = "Serve the tenant logo")
    public ResponseEntity<byte[]> logo() {
        Tenant tenant = tenantService.loadCurrent();
        byte[] bytes = logoStorage.loadOrNull(tenant);
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header("Content-Type", logoStorage.contentType(tenant))
                .body(bytes);
    }

    @DeleteMapping("/logo")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @Operation(summary = "Remove the tenant logo (owner/admin)")
    public ApiResponse<TenantResponse> deleteLogo() {
        Tenant tenant = tenantService.loadCurrent();
        logoStorage.delete(tenant);
        return ApiResponse.of(tenantService.setLogoPath(null));
    }
}
