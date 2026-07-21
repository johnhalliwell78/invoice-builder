package com.invoicebuilder.product;

import com.invoicebuilder.audit.AuditService;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.product.dto.ProductRequest;
import com.invoicebuilder.product.dto.ProductResponse;
import com.invoicebuilder.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Mock private ProductRepository productRepository;
    @Mock private AuditService auditService;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(productRepository, auditService);
        TenantContext.set(TENANT_ID);
        lenient().when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static ProductRequest request() {
        return new ProductRequest("Consulting hour", "Senior engineer time",
                new BigDecimal("120.00"), new BigDecimal("19.00"), "Services", null);
    }

    @Test
    void createBindsTenantAndDefaultsToActive() {
        ProductResponse created = service.create(request());

        assertThat(created.name()).isEqualTo("Consulting hour");
        assertThat(created.unitPrice()).isEqualByComparingTo("120.00");
        assertThat(created.taxRate()).isEqualByComparingTo("19.00");
        assertThat(created.category()).isEqualTo("Services");
        assertThat(created.active()).isTrue();
    }

    @Test
    void updateAppliesFieldsIncludingActiveFlag() {
        Product existing = new Product();
        existing.setTenantId(TENANT_ID);
        existing.setName("Old");
        existing.setActive(true);
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));

        ProductResponse updated = service.update(PRODUCT_ID, new ProductRequest(
                "New name", null, new BigDecimal("99.00"), BigDecimal.ZERO, null, false));

        assertThat(updated.name()).isEqualTo("New name");
        assertThat(updated.active()).isFalse();
    }

    @Test
    void deleteDeactivatesInsteadOfRemoving() {
        Product existing = new Product();
        existing.setTenantId(TENANT_ID);
        existing.setName("Keep me");
        existing.setActive(true);
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));

        service.delete(PRODUCT_ID);

        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void getUnknownProductThrows() {
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(PRODUCT_ID)).isInstanceOf(AppException.class);
    }
}
