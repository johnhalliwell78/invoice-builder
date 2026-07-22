package com.invoicebuilder.recurring;

import com.invoicebuilder.common.dto.ApiResponse;
import com.invoicebuilder.common.dto.PageResponse;
import com.invoicebuilder.recurring.dto.RecurringInvoiceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recurring")
@Tag(name = "Recurring invoices")
public class RecurringInvoiceController {

    private final RecurringInvoiceService recurringService;

    public RecurringInvoiceController(RecurringInvoiceService recurringService) {
        this.recurringService = recurringService;
    }

    @GetMapping
    @Operation(summary = "List recurring schedules")
    public ApiResponse<PageResponse<RecurringInvoiceResponse>> list(
            @PageableDefault(size = 20, sort = "nextRun", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.of(PageResponse.of(recurringService.list(pageable)));
    }

    @PostMapping("/{id}/toggle")
    @Operation(summary = "Pause or resume a recurring schedule")
    public ApiResponse<RecurringInvoiceResponse> toggle(@PathVariable UUID id) {
        return ApiResponse.of(recurringService.toggle(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a recurring schedule (already generated invoices are kept)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        recurringService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
