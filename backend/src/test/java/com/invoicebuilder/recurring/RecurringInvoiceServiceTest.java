package com.invoicebuilder.recurring;

import com.invoicebuilder.audit.AuditService;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.invoice.DocType;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceLineItem;
import com.invoicebuilder.invoice.InvoiceRepository;
import com.invoicebuilder.invoice.InvoiceService;
import com.invoicebuilder.invoice.InvoiceStatus;
import com.invoicebuilder.invoice.dto.InvoiceRequest;
import com.invoicebuilder.recurring.dto.MakeRecurringRequest;
import com.invoicebuilder.recurring.dto.RecurringInvoiceResponse;
import com.invoicebuilder.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringInvoiceServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    // Fixed "today": 2026-07-22
    private static final Instant NOW = Instant.parse("2026-07-22T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 22);

    @Mock private RecurringInvoiceRepository recurringRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private InvoiceService invoiceService;
    @Mock private AuditService auditService;

    private RecurringInvoiceService service;

    @BeforeEach
    void setUp() {
        service = new RecurringInvoiceService(recurringRepository, invoiceRepository,
                customerRepository, invoiceService, auditService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        TenantContext.set(TENANT_ID);
        lenient().when(recurringRepository.save(any(RecurringInvoice.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---------- date advancing (month-end clamping) ----------

    @Test
    void advancePreservesTheAnchorDayAcrossShortMonths() {
        assertThat(RecurringInvoiceService.advance(LocalDate.of(2027, 1, 31), Frequency.MONTHLY, 31))
                .isEqualTo(LocalDate.of(2027, 2, 28));
        // The anchor day is restored once the month is long enough again.
        assertThat(RecurringInvoiceService.advance(LocalDate.of(2027, 2, 28), Frequency.MONTHLY, 31))
                .isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(RecurringInvoiceService.advance(LocalDate.of(2024, 2, 29), Frequency.YEARLY, 29))
                .isEqualTo(LocalDate.of(2025, 2, 28));
        assertThat(RecurringInvoiceService.advance(LocalDate.of(2026, 7, 22), Frequency.WEEKLY, 22))
                .isEqualTo(LocalDate.of(2026, 7, 29));
        assertThat(RecurringInvoiceService.advance(LocalDate.of(2026, 7, 22), Frequency.DAILY, 22))
                .isEqualTo(LocalDate.of(2026, 7, 23));
    }

    // ---------- make-recurring ----------

    private Invoice sourceInvoice() {
        Invoice invoice = new Invoice();
        invoice.setTenantId(TENANT_ID);
        invoice.setCustomerId(CUSTOMER_ID);
        invoice.setInvoiceNumber("INV-2026-0001");
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setCurrency("EUR");
        invoice.setTemplate("modern");
        invoice.setNotes("Monthly retainer");
        invoice.setTerms("Net 30");
        invoice.setIssueDate(LocalDate.of(2026, 6, 1));
        invoice.setDueDate(LocalDate.of(2026, 7, 1));
        invoice.setDiscountAmount(new BigDecimal("5.00"));

        InvoiceLineItem item = new InvoiceLineItem();
        item.setDescription("Retainer");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("800.00"));
        item.setTaxRate(new BigDecimal("19.00"));
        item.setDiscountPercent(BigDecimal.ZERO);
        item.setSortOrder(0);
        invoice.addLineItem(item);
        return invoice;
    }

    @Test
    void makeRecurringSnapshotsTheSourceInvoice() {
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
                .thenReturn(Optional.of(sourceInvoice()));

        RecurringInvoiceResponse created = service.makeRecurring(INVOICE_ID,
                new MakeRecurringRequest(Frequency.MONTHLY, true, null));

        ArgumentCaptor<RecurringInvoice> captor = ArgumentCaptor.forClass(RecurringInvoice.class);
        verify(recurringRepository).save(captor.capture());
        RecurringInvoice saved = captor.getValue();

        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(saved.getFrequency()).isEqualTo(Frequency.MONTHLY);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.isAutoSend()).isTrue();
        assertThat(saved.getCurrency()).isEqualTo("EUR");
        assertThat(saved.getTemplate()).isEqualTo("modern");
        assertThat(saved.getDueDays()).isEqualTo(30);
        assertThat(saved.getDiscountAmount()).isEqualByComparingTo("5.00");
        assertThat(saved.getLineItems()).hasSize(1);
        assertThat(saved.getLineItems().get(0).description()).isEqualTo("Retainer");
        // Default first run: one period after today, anchored to today's day-of-month.
        assertThat(saved.getAnchorDay()).isEqualTo(22);
        assertThat(saved.getNextRun()).isEqualTo(LocalDate.of(2026, 8, 22));
        assertThat(created.frequency()).isEqualTo(Frequency.MONTHLY);
    }

    @Test
    void makeRecurringRejectsEstimates() {
        Invoice estimate = sourceInvoice();
        estimate.setDocType(DocType.ESTIMATE);
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
                .thenReturn(Optional.of(estimate));

        assertThatThrownBy(() -> service.makeRecurring(INVOICE_ID,
                new MakeRecurringRequest(Frequency.WEEKLY, false, null)))
                .isInstanceOf(AppException.class);
    }

    // ---------- generation sweep ----------

    private RecurringInvoice schedule(LocalDate nextRun, boolean autoSend) {
        RecurringInvoice r = new RecurringInvoice();
        r.setTenantId(TENANT_ID);
        r.setCustomerId(CUSTOMER_ID);
        r.setFrequency(Frequency.MONTHLY);
        r.setAnchorDay(nextRun.getDayOfMonth());
        r.setNextRun(nextRun);
        r.setActive(true);
        r.setAutoSend(autoSend);
        r.setCurrency("EUR");
        r.setTemplate("classic");
        r.setDueDays(14);
        r.setDiscountAmount(BigDecimal.ZERO);
        r.setLineItems(List.of(new RecurringLineItem("Retainer", BigDecimal.ONE,
                new BigDecimal("800.00"), new BigDecimal("19.00"), BigDecimal.ZERO)));
        return r;
    }

    @Test
    void generateCreatesDraftWithSnapshotAndAdvancesPastToday() {
        // Catch-up case: last run was months ago; exactly one draft is created
        // and the schedule fast-forwards beyond today without spamming.
        RecurringInvoice due = schedule(LocalDate.of(2026, 5, 22), false);
        when(recurringRepository.findByTenantIdAndActiveTrueAndNextRunLessThanEqual(TENANT_ID, TODAY))
                .thenReturn(List.of(due));
        when(invoiceService.create(any(InvoiceRequest.class))).thenReturn(new Invoice());

        int created = service.generateDueForTenant(TENANT_ID, TODAY);

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<InvoiceRequest> captor = ArgumentCaptor.forClass(InvoiceRequest.class);
        verify(invoiceService).create(captor.capture());
        InvoiceRequest request = captor.getValue();
        assertThat(request.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(request.issueDate()).isEqualTo(TODAY);
        assertThat(request.dueDate()).isEqualTo(TODAY.plusDays(14));
        assertThat(request.lineItems()).hasSize(1);
        assertThat(request.lineItems().get(0).unitPrice()).isEqualByComparingTo("800.00");
        assertThat(due.getNextRun()).isEqualTo(LocalDate.of(2026, 8, 22));
        verify(invoiceService, never()).send(any(), any());
    }

    @Test
    void generateSendsWhenAutoSendIsOn() {
        RecurringInvoice due = schedule(TODAY, true);
        when(recurringRepository.findByTenantIdAndActiveTrueAndNextRunLessThanEqual(TENANT_ID, TODAY))
                .thenReturn(List.of(due));
        Invoice draft = new Invoice();
        UUID draftId = UUID.randomUUID();
        ReflectionTestUtils.setField(draft, "id", draftId);
        when(invoiceService.create(any(InvoiceRequest.class))).thenReturn(draft);

        service.generateDueForTenant(TENANT_ID, TODAY);

        verify(invoiceService).send(draftId, null);
    }

    @Test
    void generateIsolatesFailuresAndStillAdvances() {
        RecurringInvoice failing = schedule(TODAY, false);
        RecurringInvoice healthy = schedule(TODAY, false);
        when(recurringRepository.findByTenantIdAndActiveTrueAndNextRunLessThanEqual(TENANT_ID, TODAY))
                .thenReturn(List.of(failing, healthy));
        when(invoiceService.create(any(InvoiceRequest.class)))
                .thenThrow(new IllegalStateException("boom"))
                .thenReturn(new Invoice());

        int created = service.generateDueForTenant(TENANT_ID, TODAY);

        assertThat(created).isEqualTo(1);
        // The broken schedule still advances so it cannot retry-storm forever.
        assertThat(failing.getNextRun()).isAfter(TODAY);
        assertThat(healthy.getNextRun()).isAfter(TODAY);
    }

    @Test
    void toggleFlipsActiveFlag() {
        RecurringInvoice r = schedule(TODAY, false);
        UUID id = UUID.randomUUID();
        when(recurringRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(r));

        service.toggle(id);
        assertThat(r.isActive()).isFalse();
        service.toggle(id);
        assertThat(r.isActive()).isTrue();
    }

    @Test
    void unknownScheduleThrows() {
        UUID id = UUID.randomUUID();
        when(recurringRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggle(id)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(AppException.class);
    }
}
