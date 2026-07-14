package com.invoicebuilder.customer;

import com.invoicebuilder.audit.AuditAction;
import com.invoicebuilder.audit.AuditService;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.customer.dto.CustomerRequest;
import com.invoicebuilder.customer.dto.CustomerResponse;
import com.invoicebuilder.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AuditService auditService;
    private final Clock clock;

    public CustomerService(CustomerRepository customerRepository, AuditService auditService, Clock clock) {
        this.customerRepository = customerRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> list(String query, Pageable pageable) {
        UUID tenantId = TenantContext.require();
        return customerRepository.search(tenantId, query, pageable).map(CustomerResponse::from);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(UUID id) {
        return CustomerResponse.from(load(id));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Customer customer = new Customer();
        customer.setTenantId(TenantContext.require());
        apply(customer, request);
        Customer saved = customerRepository.save(customer);
        auditService.record(saved.getTenantId(), "Customer", saved.getId(), AuditAction.CREATE, null);
        return CustomerResponse.from(saved);
    }

    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer customer = load(id);
        apply(customer, request);
        auditService.record(customer.getTenantId(), "Customer", customer.getId(), AuditAction.UPDATE, null);
        return CustomerResponse.from(customer);
    }

    @Transactional
    public void delete(UUID id) {
        Customer customer = load(id);
        customer.setDeletedAt(OffsetDateTime.now(clock));
        auditService.record(customer.getTenantId(), "Customer", customer.getId(), AuditAction.DELETE, null);
    }

    private Customer load(UUID id) {
        UUID tenantId = TenantContext.require();
        return customerRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND, "Customer not found"));
    }

    private static void apply(Customer customer, CustomerRequest request) {
        customer.setName(request.name().trim());
        customer.setEmail(blankToNull(request.email()));
        customer.setPhone(blankToNull(request.phone()));
        customer.setCompany(blankToNull(request.company()));
        customer.setAddress(request.address());
        customer.setTaxId(blankToNull(request.taxId()));
        customer.setNotes(blankToNull(request.notes()));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
