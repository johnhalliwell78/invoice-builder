package com.invoicebuilder.reporting;

import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.invoice.DocType;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceRepository;
import com.invoicebuilder.invoice.InvoiceStatus;
import com.invoicebuilder.payment.Payment;
import com.invoicebuilder.payment.PaymentReversal;
import com.invoicebuilder.payment.PaymentReversalRepository;
import com.invoicebuilder.payment.PaymentRepository;
import com.invoicebuilder.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Writer;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Streams tenant data as CSV for accountants.
 *
 * <p>Written page by page rather than assembled in memory: a tenant with
 * tens of thousands of invoices should not turn an export into an
 * out-of-memory error.</p>
 */
@Service
public class ExportService {

    private static final int PAGE_SIZE = 500;

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentReversalRepository reversalRepository;

    public ExportService(InvoiceRepository invoiceRepository,
                         CustomerRepository customerRepository,
                         PaymentRepository paymentRepository,
                         PaymentReversalRepository reversalRepository) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.paymentRepository = paymentRepository;
        this.reversalRepository = reversalRepository;
    }

    @Transactional(readOnly = true)
    public void writeInvoices(Writer out, DocType docType, InvoiceStatus status,
                              LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.require();
        try (CsvWriter csv = new CsvWriter(out)) {
            csv.writeRow("Number", "Status", "Customer", "Currency", "Issue date", "Due date",
                    "Subtotal", "Tax", "Discount", "Total", "Amount paid", "Balance");

            int page = 0;
            Page<Invoice> slice;
            do {
                slice = invoiceRepository.search(tenantId,
                        docType == null ? DocType.INVOICE : docType,
                        status, null, from, to,
                        PageRequest.of(page, PAGE_SIZE, Sort.by("issueDate").ascending()));
                Map<UUID, String> names = customerNames(tenantId, slice);
                for (Invoice invoice : slice) {
                    csv.writeRow(
                            invoice.getInvoiceNumber(),
                            invoice.getStatus().name(),
                            names.getOrDefault(invoice.getCustomerId(), ""),
                            invoice.getCurrency(),
                            String.valueOf(invoice.getIssueDate()),
                            String.valueOf(invoice.getDueDate()),
                            invoice.getSubtotal().toPlainString(),
                            invoice.getTaxTotal().toPlainString(),
                            invoice.getDiscountAmount().toPlainString(),
                            invoice.getTotal().toPlainString(),
                            invoice.getAmountPaid().toPlainString(),
                            invoice.getTotal().subtract(invoice.getAmountPaid()).toPlainString());
                }
                csv.flush();
                page++;
            } while (slice.hasNext());
        }
    }

    /**
     * Payments received in a period, with reversals as negative rows so the
     * file reconciles against each invoice's recorded {@code amountPaid}.
     */
    @Transactional(readOnly = true)
    public void writePayments(Writer out, LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.require();
        try (CsvWriter csv = new CsvWriter(out)) {
            csv.writeRow("Date", "Invoice", "Type", "Method or reason", "Amount", "Currency", "Note");

            int page = 0;
            Page<Payment> slice;
            do {
                slice = paymentRepository.findByTenantId(tenantId,
                        PageRequest.of(page, PAGE_SIZE, Sort.by("paidOn").ascending()));
                Map<UUID, Invoice> invoices = invoicesFor(slice);
                // One query per page, not per payment.
                Map<UUID, List<PaymentReversal>> reversals = reversalsFor(tenantId, slice);
                for (Payment payment : slice) {
                    if (outsidePeriod(payment.getPaidOn(), from, to)) {
                        continue;
                    }
                    Invoice invoice = invoices.get(payment.getInvoiceId());
                    csv.writeRow(
                            String.valueOf(payment.getPaidOn()),
                            invoice == null ? "" : invoice.getInvoiceNumber(),
                            "PAYMENT",
                            payment.getMethod().name(),
                            payment.getAmount().toPlainString(),
                            invoice == null ? "" : invoice.getCurrency(),
                            payment.getNote());

                    for (PaymentReversal reversal :
                            reversals.getOrDefault(payment.getId(), List.of())) {
                        csv.writeRow(
                                String.valueOf(reversal.getCreatedAt().toLocalDate()),
                                invoice == null ? "" : invoice.getInvoiceNumber(),
                                "REVERSAL",
                                reversal.getReason().name(),
                                reversal.getAmount().negate().toPlainString(),
                                invoice == null ? "" : invoice.getCurrency(),
                                reversal.getNote());
                    }
                }
                csv.flush();
                page++;
            } while (slice.hasNext());
        }
    }

    private static boolean outsidePeriod(LocalDate date, LocalDate from, LocalDate to) {
        return (from != null && date.isBefore(from)) || (to != null && date.isAfter(to));
    }

    private Map<UUID, String> customerNames(UUID tenantId, Page<Invoice> slice) {
        Set<UUID> ids = slice.getContent().stream()
                .map(Invoice::getCustomerId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return customerRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName));
    }

    private Map<UUID, List<PaymentReversal>> reversalsFor(UUID tenantId, Page<Payment> slice) {
        Set<UUID> ids = slice.getContent().stream().map(Payment::getId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return reversalRepository.findByTenantIdAndPaymentIdIn(tenantId, ids).stream()
                .collect(Collectors.groupingBy(PaymentReversal::getPaymentId));
    }

    private Map<UUID, Invoice> invoicesFor(Page<Payment> slice) {
        Set<UUID> ids = slice.getContent().stream()
                .map(Payment::getInvoiceId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return invoiceRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Invoice::getId, i -> i));
    }
}
