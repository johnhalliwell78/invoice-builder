package com.invoicebuilder.product;

import com.invoicebuilder.audit.AuditAction;
import com.invoicebuilder.audit.AuditService;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.product.dto.ProductRequest;
import com.invoicebuilder.product.dto.ProductResponse;
import com.invoicebuilder.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final AuditService auditService;

    public ProductService(ProductRepository productRepository, AuditService auditService) {
        this.productRepository = productRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(String query, boolean activeOnly, Pageable pageable) {
        UUID tenantId = TenantContext.require();
        return productRepository.search(tenantId, query, activeOnly, pageable)
                .map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return ProductResponse.from(load(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        product.setTenantId(TenantContext.require());
        apply(product, request);
        Product saved = productRepository.save(product);
        auditService.record(saved.getTenantId(), "Product", saved.getId(), AuditAction.CREATE, null);
        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = load(id);
        apply(product, request);
        auditService.record(product.getTenantId(), "Product", product.getId(), AuditAction.UPDATE, null);
        return ProductResponse.from(product);
    }

    /** Deactivates instead of removing — invoices may reference the price history. */
    @Transactional
    public void delete(UUID id) {
        Product product = load(id);
        product.setActive(false);
        auditService.record(product.getTenantId(), "Product", product.getId(), AuditAction.DELETE, null);
    }

    private Product load(UUID id) {
        UUID tenantId = TenantContext.require();
        return productRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found"));
    }

    private static void apply(Product product, ProductRequest request) {
        product.setName(request.name().trim());
        product.setDescription(blankToNull(request.description()));
        product.setUnitPrice(request.unitPrice());
        product.setTaxRate(request.taxRate() == null ? BigDecimal.ZERO : request.taxRate());
        product.setCategory(blankToNull(request.category()));
        if (request.active() != null) {
            product.setActive(request.active());
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
