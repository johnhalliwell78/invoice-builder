package com.invoicebuilder.invoice;

import com.invoicebuilder.common.dto.ApiResponse;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.invoice.dto.PublicInvoiceResponse;
import com.invoicebuilder.tenant.Tenant;
import com.invoicebuilder.tenant.TenantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Anonymous endpoint for invoice recipients who land on the link in the
 * sent email. Looks the invoice up by its opaque {@code publicToken} and,
 * on first view from a SENT invoice, transitions it to VIEWED so the
 * issuer's dashboard updates.
 */
@Controller
@RequestMapping("/api/v1/public/invoices")
@Tag(name = "Public invoice view", description = "Anonymous, token-scoped read access for recipients")
public class PublicInvoiceController {

    private final InvoiceRepository invoiceRepository;
    private final TenantRepository tenantRepository;
    private final CustomerRepository customerRepository;
    private final Clock clock;

    public PublicInvoiceController(InvoiceRepository invoiceRepository,
                                   TenantRepository tenantRepository,
                                   CustomerRepository customerRepository,
                                   Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.tenantRepository = tenantRepository;
        this.customerRepository = customerRepository;
        this.clock = clock;
    }

    @GetMapping("/{token}")
    @Operation(summary = "View an invoice by its public token (anonymous)")
    @ResponseBody
    @Transactional
    public ApiResponse<PublicInvoiceResponse> view(@PathVariable String token) {
        Invoice invoice = invoiceRepository.findByPublicToken(token)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND, "Invoice not found"));

        // First view of a SENT invoice → mark VIEWED.
        if (invoice.getStatus() == InvoiceStatus.SENT) {
            invoice.setStatus(InvoiceStatus.VIEWED);
            invoice.setViewedAt(OffsetDateTime.now(clock));
        }

        Tenant tenant = tenantRepository.findById(invoice.getTenantId())
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND, "Tenant not found"));
        Customer customer = customerRepository.findById(invoice.getCustomerId())
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND, "Customer not found"));

        return ApiResponse.of(PublicInvoiceResponse.of(invoice, tenant, customer));
    }
}
