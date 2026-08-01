package com.invoicebuilder.apikey;

import com.invoicebuilder.apikey.dto.ApiKeyCreatedResponse;
import com.invoicebuilder.apikey.dto.ApiKeyRequest;
import com.invoicebuilder.apikey.dto.ApiKeyResponse;
import com.invoicebuilder.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Owners and admins only: a key is a credential for the whole tenant. */
@RestController
@RequestMapping("/api/v1/api-keys")
@Tag(name = "API keys")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping
    @Operation(summary = "List API keys (secrets are never returned)")
    public ApiResponse<List<ApiKeyResponse>> list() {
        return ApiResponse.of(apiKeyService.list());
    }

    @PostMapping
    @Operation(summary = "Create an API key — the secret is shown only in this response")
    public ResponseEntity<ApiResponse<ApiKeyCreatedResponse>> create(
            @Valid @RequestBody ApiKeyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(apiKeyService.create(request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke an API key immediately")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        apiKeyService.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
