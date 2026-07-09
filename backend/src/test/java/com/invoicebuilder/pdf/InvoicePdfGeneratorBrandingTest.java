package com.invoicebuilder.pdf;

import com.invoicebuilder.config.AppProperties;
import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceLineItem;
import com.invoicebuilder.tenant.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests: rendering with branding (logo, accent color, footer, payment
 * info) must produce a parseable PDF and never throw. Visual output is not
 * asserted — that would be brittle; this guards the wiring.
 */
class InvoicePdfGeneratorBrandingTest {

    private static byte[] tinyPng() {
        try {
            java.awt.image.BufferedImage img =
                    new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private InvoicePdfGenerator generator;
    private Invoice invoice;
    private Tenant tenant;
    private Customer customer;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("0123456789abcdef0123456789abcdef", Duration.ofMinutes(15), Duration.ofDays(7), "test"),
                null,
                new AppProperties.Sendgrid("", "noreply@test.local", "Test"),
                new AppProperties.Storage(Path.of("./build/tmp"), Path.of("./build/tmp")),
                new AppProperties.Cors(List.of()),
                new AppProperties.RateLimit(5, Duration.ofMinutes(15), 100));
        generator = new InvoicePdfGenerator(new StaticMessageSource(), properties);

        tenant = new Tenant();
        tenant.setName("Acme GmbH");
        tenant.setDefaultLocale("en");
        tenant.setBrandingColor("#FF5733");
        tenant.setFooterText("Acme GmbH · Registered in Berlin HRB 12345");
        tenant.setPaymentInfo("IBAN DE89 3704 0044 0532 0130 00\nBIC COBADEFF");

        customer = new Customer();
        customer.setName("Widget Co");

        invoice = new Invoice();
        invoice.setTenantId(tenant.getId());
        invoice.setInvoiceNumber("INV-2026-0042");
        invoice.setCurrency("EUR");
        invoice.setIssueDate(LocalDate.of(2026, 7, 9));
        invoice.setDueDate(LocalDate.of(2026, 8, 8));
        InvoiceLineItem item = new InvoiceLineItem();
        item.setDescription("Consulting");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setTaxRate(new BigDecimal("19.00"));
        item.setAmount(new BigDecimal("100.00"));
        invoice.addLineItem(item);
        invoice.setSubtotal(new BigDecimal("100.00"));
        invoice.setTaxTotal(new BigDecimal("19.00"));
        invoice.setTotal(new BigDecimal("119.00"));
    }

    @Test
    void rendersBrandedPdfWithLogoForBothTemplates() {
        byte[] logo = tinyPng();
        for (String template : InvoicePdfGenerator.TEMPLATES) {
            byte[] pdf = generator.render(invoice, tenant, customer, template, logo);
            assertThat(pdf).isNotEmpty();
            assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        }
    }

    @Test
    void rendersWithoutBrandingOrLogo() {
        tenant.setBrandingColor(null);
        tenant.setFooterText(null);
        tenant.setPaymentInfo(null);

        byte[] pdf = generator.render(invoice, tenant, customer, null, null);

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void ignoresMalformedBrandingColor() {
        tenant.setBrandingColor("not-a-color");

        byte[] pdf = generator.render(invoice, tenant, customer, "modern", null);

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }
}
