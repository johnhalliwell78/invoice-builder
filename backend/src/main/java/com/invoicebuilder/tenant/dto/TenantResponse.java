package com.invoicebuilder.tenant.dto;

import com.invoicebuilder.common.Address;
import com.invoicebuilder.tenant.Tenant;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        String slug,
        String defaultCurrency,
        String defaultLocale,
        String logoPath,
        Address address,
        String taxId,
        String invoicePrefix,
        String defaultTemplate,
        String footerText,
        String paymentInfo,
        String brandingColor,
        int nextInvoiceNumber,
        OffsetDateTime createdAt
) {

    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getDefaultCurrency(),
                tenant.getDefaultLocale(),
                tenant.getLogoPath(),
                tenant.getAddress(),
                tenant.getTaxId(),
                tenant.getInvoicePrefix(),
                tenant.getDefaultTemplate(),
                tenant.getFooterText(),
                tenant.getPaymentInfo(),
                tenant.getBrandingColor(),
                tenant.getNextInvoiceNumber(),
                tenant.getCreatedAt()
        );
    }
}
