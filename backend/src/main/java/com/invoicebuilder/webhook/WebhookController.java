package com.invoicebuilder.webhook;

import com.invoicebuilder.common.dto.ApiResponse;
import com.invoicebuilder.webhook.dto.WebhookEndpointCreatedResponse;
import com.invoicebuilder.webhook.dto.WebhookEndpointRequest;
import com.invoicebuilder.webhook.dto.WebhookEndpointResponse;
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

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Outbound webhooks")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @GetMapping
    @Operation(summary = "List webhook endpoints")
    public ApiResponse<List<WebhookEndpointResponse>> list() {
        return ApiResponse.of(webhookService.list());
    }

    @PostMapping
    @Operation(summary = "Register a webhook endpoint (public http(s) URLs only)")
    public ResponseEntity<ApiResponse<WebhookEndpointCreatedResponse>> create(
            @Valid @RequestBody WebhookEndpointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(webhookService.create(request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a webhook endpoint")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        webhookService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
