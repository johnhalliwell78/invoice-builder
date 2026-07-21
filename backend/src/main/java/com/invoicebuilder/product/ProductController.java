package com.invoicebuilder.product;

import com.invoicebuilder.common.dto.ApiResponse;
import com.invoicebuilder.common.dto.PageResponse;
import com.invoicebuilder.product.dto.ProductRequest;
import com.invoicebuilder.product.dto.ProductResponse;
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
@RequestMapping("/api/v1/products")
@Tag(name = "Products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "List products (paginated, optional search across name and category)")
    public ApiResponse<PageResponse<ProductResponse>> list(
            @Parameter(description = "Search across name, category")
            @RequestParam(name = "q", required = false) String query,
            @Parameter(description = "When true, only active products are returned")
            @RequestParam(name = "activeOnly", required = false, defaultValue = "false") boolean activeOnly,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.of(PageResponse.of(productService.list(query, activeOnly, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product by id")
    public ApiResponse<ProductResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(productService.get(id));
    }

    @PostMapping
    @Operation(summary = "Create a product")
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a product")
    public ApiResponse<ProductResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody ProductRequest request) {
        return ApiResponse.of(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a product (kept for invoice history)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
