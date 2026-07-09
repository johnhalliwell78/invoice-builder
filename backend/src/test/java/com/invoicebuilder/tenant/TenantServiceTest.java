package com.invoicebuilder.tenant;

import com.invoicebuilder.common.Address;
import com.invoicebuilder.tenant.dto.TenantResponse;
import com.invoicebuilder.tenant.dto.TenantUpdateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock private TenantRepository tenantRepository;

    private TenantService service;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        service = new TenantService(tenantRepository);
        TenantContext.set(TENANT_ID);
        tenant = new Tenant();
        tenant.setName("Acme");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void updateAppliesBrandingAndCompanyFields() {
        TenantUpdateRequest request = new TenantUpdateRequest(
                "Acme GmbH", "EUR", "de", "DE123456789", "ACME", "modern",
                new Address("Hauptstr. 1", "Berlin", null, "10115", "Germany"),
                "Thank you for your business — Acme GmbH",
                "IBAN DE89 3704 0044 0532 0130 00 · BIC COBADEFF",
                "#FF5733");

        TenantResponse response = service.update(request);

        assertThat(response.name()).isEqualTo("Acme GmbH");
        assertThat(response.defaultTemplate()).isEqualTo("modern");
        assertThat(response.footerText()).isEqualTo("Thank you for your business — Acme GmbH");
        assertThat(response.paymentInfo()).startsWith("IBAN");
        assertThat(response.brandingColor()).isEqualTo("#FF5733");
        assertThat(tenant.getBrandingColor()).isEqualTo("#FF5733");
    }

    @Test
    void updateClearsBrandingFieldsWhenBlank() {
        tenant.setFooterText("old footer");
        tenant.setBrandingColor("#000000");
        TenantUpdateRequest request = new TenantUpdateRequest(
                "Acme", null, null, null, null, null, null, "", "", null);

        service.update(request);

        assertThat(tenant.getFooterText()).isNull();
        assertThat(tenant.getBrandingColor()).isEqualTo("#000000");
    }
}
