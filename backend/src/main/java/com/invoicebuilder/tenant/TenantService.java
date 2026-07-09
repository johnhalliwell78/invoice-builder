package com.invoicebuilder.tenant;

import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.tenant.dto.TenantResponse;
import com.invoicebuilder.tenant.dto.TenantUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public TenantResponse getCurrent() {
        return TenantResponse.from(load(TenantContext.require()));
    }

    @Transactional
    public TenantResponse update(TenantUpdateRequest request) {
        Tenant tenant = load(TenantContext.require());
        tenant.setName(request.name().trim());
        if (request.defaultCurrency() != null) {
            tenant.setDefaultCurrency(request.defaultCurrency().toUpperCase());
        }
        if (request.defaultLocale() != null) {
            tenant.setDefaultLocale(request.defaultLocale());
        }
        if (request.taxId() != null) {
            tenant.setTaxId(request.taxId().isBlank() ? null : request.taxId().trim());
        }
        if (request.invoicePrefix() != null) {
            tenant.setInvoicePrefix(request.invoicePrefix().trim());
        }
        if (request.defaultTemplate() != null) {
            tenant.setDefaultTemplate(request.defaultTemplate());
        }
        if (request.address() != null) {
            tenant.setAddress(request.address());
        }
        if (request.footerText() != null) {
            tenant.setFooterText(request.footerText().isBlank() ? null : request.footerText().trim());
        }
        if (request.paymentInfo() != null) {
            tenant.setPaymentInfo(request.paymentInfo().isBlank() ? null : request.paymentInfo().trim());
        }
        if (request.brandingColor() != null) {
            tenant.setBrandingColor(request.brandingColor().toUpperCase());
        }
        return TenantResponse.from(tenant);
    }

    private Tenant load(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND, "Tenant not found"));
    }
}
