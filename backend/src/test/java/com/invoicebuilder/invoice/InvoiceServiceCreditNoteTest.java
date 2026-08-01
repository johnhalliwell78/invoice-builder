package com.invoicebuilder.invoice;

import com.invoicebuilder.audit.AuditService;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.email.EmailService;
import com.invoicebuilder.pdf.InvoicePdfGenerator;
import com.invoicebuilder.pdf.PdfStorage;
import com.invoicebuilder.tenant.LogoStorage;
import com.invoicebuilder.tenant.TenantContext;
import com.invoicebuilder.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * A credit note reduces what is <em>owed</em>; a refund returns money that was
 * <em>collected</em>. These tests pin that distinction.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceServiceCreditNoteTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID CREDIT_NOTE_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-27T10:00:00Z");

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private InvoiceNumberGenerator numberGenerator;
    @Mock private InvoicePdfGenerator pdfGenerator;
    @Mock private PdfStorage pdfStorage;
    @Mock private LogoStorage logoStorage;
    @Mock private EmailService emailService;
    @Mock private MessageSource messages;
    @Mock private InvoiceReminderRepository reminderRepository;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private InvoiceService service;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoiceRepository, customerRepository, tenantRepository,
                numberGenerator, new InvoiceCalculator(), pdfGenerator, pdfStorage, logoStorage,
                emailService, messages, reminderRepository, auditService, eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        TenantContext.set(TENANT_ID);

        invoice = new Invoice();
        ReflectionTestUtils.setField(invoice, "id", INVOICE_ID);
        invoice.setTenantId(TENANT_ID);
        invoice.setCustomerId(CUSTOMER_ID);
        invoice.setInvoiceNumber("INV-2026-0001");
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setCurrency("EUR");
        invoice.setTemplate("classic");
        invoice.setTotal(new BigDecimal("119.00"));
        invoice.setSubtotal(new BigDecimal("100.00"));
        invoice.setTaxTotal(new BigDecimal("19.00"));
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.setIssueDate(LocalDate.of(2026, 7, 1));
        invoice.setDueDate(LocalDate.of(2026, 7, 31));

        InvoiceLineItem item = new InvoiceLineItem();
        item.setDescription("Consulting");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setTaxRate(new BigDecimal("19.00"));
        item.setDiscountPercent(BigDecimal.ZERO);
        item.setSortOrder(0);
        invoice.addLineItem(item);

        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.findByIdAndTenantIdForUpdate(INVOICE_ID, TENANT_ID))
                .thenReturn(Optional.of(invoice));
        when(numberGenerator.reserveNextCreditNote(TENANT_ID)).thenReturn("CN-2026-0001");
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Invoice creditNoteFor(String total) {
        Invoice note = new Invoice();
        ReflectionTestUtils.setField(note, "id", CREDIT_NOTE_ID);
        note.setTenantId(TENANT_ID);
        note.setCustomerId(CUSTOMER_ID);
        note.setDocType(DocType.CREDIT_NOTE);
        note.setCreditedInvoiceId(INVOICE_ID);
        note.setInvoiceNumber("CN-2026-0001");
        note.setStatus(InvoiceStatus.DRAFT);
        note.setCurrency("EUR");
        note.setTotal(new BigDecimal(total));
        note.setIssueDate(LocalDate.of(2026, 7, 27));
        note.setDueDate(LocalDate.of(2026, 7, 27));
        when(invoiceRepository.findByIdAndTenantId(CREDIT_NOTE_ID, TENANT_ID)).thenReturn(Optional.of(note));
        return note;
    }

    @Test
    void creditNoteCopiesTheInvoiceWithItsOwnNumberSeries() {
        Invoice note = service.createCreditNote(INVOICE_ID);

        assertThat(note.getDocType()).isEqualTo(DocType.CREDIT_NOTE);
        assertThat(note.getInvoiceNumber()).isEqualTo("CN-2026-0001");
        assertThat(note.getCreditedInvoiceId()).isEqualTo(INVOICE_ID);
        assertThat(note.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(note.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(note.getCurrency()).isEqualTo("EUR");
        assertThat(note.getTotal()).isEqualByComparingTo("119.00");
    }

    @Test
    void creditNotesCannotBeRaisedAgainstEstimatesOrOtherCreditNotes() {
        invoice.setDocType(DocType.ESTIMATE);
        assertThatThrownBy(() -> service.createCreditNote(INVOICE_ID)).isInstanceOf(AppException.class);

        invoice.setDocType(DocType.CREDIT_NOTE);
        assertThatThrownBy(() -> service.createCreditNote(INVOICE_ID)).isInstanceOf(AppException.class);
    }

    @Test
    void aDraftCreditNoteChangesNothingUntilItIsIssued() {
        creditNoteFor("119.00");

        assertThat(invoice.getCreditedAmount()).isEqualByComparingTo("0");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
    }

    @Test
    void issuingAFullCreditNoteSettlesTheInvoiceWithoutClaimingRevenue() {
        creditNoteFor("119.00");

        service.issueCreditNote(CREDIT_NOTE_ID);

        assertThat(invoice.getCreditedAmount()).isEqualByComparingTo("119.00");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        // Load-bearing: revenue reporting keys on paidAt, so an invoice
        // written off by credit must never look like money received.
        assertThat(invoice.getPaidAt()).isNull();
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("0");
    }

    @Test
    void aPartialCreditNoteLeavesTheInvoiceOpenForTheRemainder() {
        creditNoteFor("19.00");

        service.issueCreditNote(CREDIT_NOTE_ID);

        assertThat(invoice.getCreditedAmount()).isEqualByComparingTo("19.00");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
    }

    @Test
    void creditPlusPaymentTogetherSettleTheInvoice() {
        invoice.setAmountPaid(new BigDecimal("100.00"));
        creditNoteFor("19.00");

        service.issueCreditNote(CREDIT_NOTE_ID);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        // Money really was received here, so the timestamp is set.
        assertThat(invoice.getPaidAt()).isNotNull();
    }

    @Test
    void issuingTheSameCreditNoteTwiceCannotDoubleCredit() {
        Invoice note = creditNoteFor("119.00");
        service.issueCreditNote(CREDIT_NOTE_ID);
        note.setStatus(InvoiceStatus.SENT);

        assertThatThrownBy(() -> service.issueCreditNote(CREDIT_NOTE_ID)).isInstanceOf(AppException.class);
        assertThat(invoice.getCreditedAmount()).isEqualByComparingTo("119.00");
    }

    @Test
    void creditNotesCannotExceedWhatIsStillOwed() {
        creditNoteFor("500.00");

        assertThatThrownBy(() -> service.issueCreditNote(CREDIT_NOTE_ID)).isInstanceOf(AppException.class);
    }

    // ---------- cancellation guard ----------

    @Test
    void anInvoiceHoldingPaymentsCannotBeCancelledOutright() {
        // Cancelling would strand the money; refund or credit it first.
        invoice.setAmountPaid(new BigDecimal("50.00"));

        assertThatThrownBy(() -> service.cancel(INVOICE_ID)).isInstanceOf(AppException.class);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
    }

    @Test
    void anUnpaidInvoiceStillCancelsNormally() {
        Invoice cancelled = service.cancel(INVOICE_ID);

        assertThat(cancelled.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
    }
}
