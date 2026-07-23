package com.invoicebuilder.it;

import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.invoice.DocType;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceRepository;
import com.invoicebuilder.invoice.InvoiceService;
import com.invoicebuilder.invoice.InvoiceStatus;
import com.invoicebuilder.invoice.dto.InvoiceRequest;
import com.invoicebuilder.invoice.dto.LineItemRequest;
import com.invoicebuilder.tenant.Tenant;
import com.invoicebuilder.tenant.TenantContext;
import com.invoicebuilder.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Base for integration tests: real Spring context, real transaction proxies,
 * real Postgres via Testcontainers. Booting the context applies every
 * Liquibase changelog — a standing migration test.
 *
 * <p>One container is shared across all IT classes (singleton pattern; the
 * lazy start in {@link #datasource} keeps Docker-less machines skipping
 * instead of failing during class init). Without Docker these tests are
 * skipped, not failed — CI always has Docker and always runs them.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTestBase {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected TenantRepository tenantRepository;
    @Autowired protected CustomerRepository customerRepository;
    @Autowired protected InvoiceRepository invoiceRepository;
    @Autowired protected InvoiceService invoiceService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    protected Tenant createTenant() {
        Tenant tenant = new Tenant();
        tenant.setName("IT Tenant");
        tenant.setSlug("it-" + UUID.randomUUID());
        return tenantRepository.save(tenant);
    }

    protected Customer createCustomer(Tenant tenant, String name) {
        Customer customer = new Customer();
        customer.setTenantId(tenant.getId());
        customer.setName(name);
        customer.setEmail(name.toLowerCase().replace(' ', '.') + "@example.test");
        return customerRepository.save(customer);
    }

    protected void softDelete(Customer customer) {
        customer.setDeletedAt(OffsetDateTime.now());
        customerRepository.save(customer);
    }

    /** Creates a document through the real service, then force-sets a status. */
    protected Invoice createDocument(Tenant tenant, Customer customer, DocType docType,
                                     String unitPrice, InvoiceStatus status) {
        TenantContext.set(tenant.getId());
        try {
            Invoice created = invoiceService.create(new InvoiceRequest(
                    customer.getId(), "EUR",
                    LocalDate.now(), LocalDate.now().plusDays(14),
                    List.of(new LineItemRequest("IT line", BigDecimal.ONE,
                            new BigDecimal(unitPrice), BigDecimal.ZERO, BigDecimal.ZERO)),
                    BigDecimal.ZERO, null, null, "classic", docType));
            if (status != InvoiceStatus.DRAFT) {
                created.setStatus(status);
                created = invoiceRepository.save(created);
            }
            return created;
        } finally {
            TenantContext.clear();
        }
    }
}
