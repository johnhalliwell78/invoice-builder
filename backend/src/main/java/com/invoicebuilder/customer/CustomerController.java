package com.invoicebuilder.customer;

import com.invoicebuilder.common.dto.ApiResponse;
import com.invoicebuilder.common.dto.PageResponse;
import com.invoicebuilder.customer.dto.CustomerRequest;
import com.invoicebuilder.customer.dto.CustomerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @Operation(summary = "List customers (paginated, optional fuzzy search)")
    public ApiResponse<PageResponse<CustomerResponse>> list(
            @Parameter(description = "Search across name, email, company")
            @RequestParam(name = "q", required = false) String query,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.of(PageResponse.of(customerService.list(query, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a customer by id")
    public ApiResponse<CustomerResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(customerService.get(id));
    }

    @PostMapping
    @Operation(summary = "Create a customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> create(@Valid @RequestBody CustomerRequest request) {
        CustomerResponse created = customerService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a customer")
    public ApiResponse<CustomerResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody CustomerRequest request) {
        return ApiResponse.of(customerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a customer")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
