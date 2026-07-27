package com.invoicebuilder.payment;

import com.invoicebuilder.audit.AuditAction;
import com.invoicebuilder.audit.AuditService;
import com.invoicebuilder.auth.UserPrincipal;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.invoice.DocType;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceRepository;
import com.invoicebuilder.invoice.InvoiceStatus;
import com.invoicebuilder.notification.NotificationEvent;
import com.invoicebuilder.notification.NotificationType;
import com.invoicebuilder.payment.dto.PaymentRequest;
import com.invoicebuilder.payment.dto.PaymentResponse;
import com.invoicebuilder.payment.dto.ReversalRequest;
import com.invoicebuilder.payment.dto.ReversalResponse;
import com.invoicebuilder.tenant.TenantContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Set<InvoiceStatus> OPEN_STATUSES =
            EnumSet.of(InvoiceStatus.SENT, InvoiceStatus.VIEWED, InvoiceStatus.OVERDUE);

    private final PaymentRepository paymentRepository;
    private final PaymentReversalRepository reversalRepository;
    private final InvoiceRepository invoiceRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentReversalRepository reversalRepository,
                          InvoiceRepository invoiceRepository,
                          AuditService auditService,
                          ApplicationEventPublisher eventPublisher,
                          Clock clock) {
        this.paymentRepository = paymentRepository;
        this.reversalRepository = reversalRepository;
        this.invoiceRepository = invoiceRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Records a payment against an open invoice. The invoice's amountPaid
     * accumulates; when it reaches the total the invoice transitions to PAID.
     * Over-payments are rejected. Payments are append-only (no delete).
     */
    @Transactional
    public PaymentResponse record(UUID invoiceId, PaymentRequest request) {
        Invoice invoice = loadOpenInvoice(invoiceId);
        BigDecimal balance = invoice.getTotal().subtract(invoice.getAmountPaid());
        if (request.amount().compareTo(balance) > 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Payment of %s exceeds the remaining balance of %s"
                            .formatted(request.amount(), balance));
        }

        Payment payment = new Payment();
        payment.setTenantId(invoice.getTenantId());
        payment.setInvoiceId(invoice.getId());
        payment.setAmount(request.amount());
        payment.setMethod(request.method());
        payment.setPaidOn(request.paidOn() != null ? request.paidOn() : LocalDate.now(clock));
        payment.setNote(request.note());
        payment.setCreatedBy(currentUserId());
        Payment saved = paymentRepository.save(payment);

        invoice.setAmountPaid(invoice.getAmountPaid().add(request.amount()));
        if (invoice.getAmountPaid().compareTo(invoice.getTotal()) >= 0) {
            transitionToPaid(invoice);
        } else {
            auditService.record(invoice.getTenantId(), "Invoice", invoice.getId(),
                    AuditAction.UPDATE, Map.<String, Object>of(
                            "paymentAmount", request.amount().toPlainString(),
                            "amountPaid", invoice.getAmountPaid().toPlainString()));
        }
        return PaymentResponse.from(saved);
    }

    /**
     * The former "mark paid": records the whole remaining balance as one
     * payment. A zero-total (or already covered) open invoice has nothing to
     * record — it transitions straight to PAID, preserving the pre-payments
     * behavior for free/goodwill invoices.
     */
    @Transactional
    public void markRemainingPaid(UUID invoiceId) {
        Invoice invoice = loadOpenInvoice(invoiceId);
        BigDecimal balance = invoice.getTotal().subtract(invoice.getAmountPaid());
        if (balance.compareTo(BigDecimal.ZERO) > 0) {
            record(invoiceId, new PaymentRequest(balance, PaymentMethod.OTHER, null, null));
        } else {
            transitionToPaid(invoice);
        }
    }

    /**
     * Books a payment that an external processor has already collected
     * (Stripe Checkout). Deliberately different from {@link #record}:
     *
     * <ul>
     *   <li>No over-payment guard — that guard protects against typos in
     *       hand-entered amounts. Here the money is a fact; refusing it would
     *       make our books disagree with the processor. An excess is audited
     *       so it can be refunded out of band.</li>
     *   <li>Idempotent on {@code externalId} — webhook redelivery is normal,
     *       and a unique index backs this check at the database level.</li>
     *   <li>Rejects a currency that differs from the invoice's, rather than
     *       adding foreign-denominated money to the ledger.</li>
     *   <li>Accepts any non-estimate document regardless of status, so a
     *       payment that lands after a manual mark-paid is still recorded
     *       rather than lost.</li>
     * </ul>
     *
     * @return the booked payment, or empty when {@code externalId} was already recorded
     */
    @Transactional
    public Optional<PaymentResponse> recordExternal(UUID invoiceId, BigDecimal amount,
                                                    String currency, PaymentMethod method,
                                                    String externalId, String note) {
        if (externalId != null && paymentRepository.existsByExternalId(externalId)) {
            return Optional.empty();
        }
        UUID tenantId = TenantContext.require();
        Invoice invoice = invoiceRepository.findByIdAndTenantIdForUpdate(invoiceId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND, "Invoice not found"));
        if (invoice.getDocType() != DocType.INVOICE) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Payments only apply to invoices");
        }
        // Adding a USD amount to a EUR ledger would be silently wrong money.
        if (currency != null && !currency.equalsIgnoreCase(invoice.getCurrency())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Payment currency %s does not match invoice currency %s"
                            .formatted(currency, invoice.getCurrency()));
        }

        Payment payment = new Payment();
        payment.setTenantId(invoice.getTenantId());
        payment.setInvoiceId(invoice.getId());
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setPaidOn(LocalDate.now(clock));
        payment.setNote(note);
        payment.setExternalId(externalId);
        Payment saved = paymentRepository.save(payment);

        BigDecimal updatedPaid = invoice.getAmountPaid().add(amount);
        invoice.setAmountPaid(updatedPaid);
        if (updatedPaid.compareTo(invoice.getTotal()) > 0) {
            auditService.record(invoice.getTenantId(), "Invoice", invoice.getId(),
                    AuditAction.UPDATE, Map.<String, Object>of(
                            "overpaid", updatedPaid.subtract(invoice.getTotal()).toPlainString(),
                            "externalId", String.valueOf(externalId)));
        }
        if (updatedPaid.compareTo(invoice.getTotal()) >= 0
                && invoice.getStatus().canTransitionTo(invoice.getDocType(), InvoiceStatus.PAID)) {
            transitionToPaid(invoice);
        } else {
            auditService.record(invoice.getTenantId(), "Invoice", invoice.getId(),
                    AuditAction.UPDATE, Map.<String, Object>of(
                            "paymentAmount", amount.toPlainString(),
                            "amountPaid", updatedPaid.toPlainString()));
        }
        return Optional.of(PaymentResponse.from(saved));
    }

    /**
     * Records money going back out against a specific payment: an operator
     * refund, or a bookkeeping correction.
     */
    @Transactional
    public ReversalResponse recordReversal(UUID paymentId, ReversalRequest request) {
        UUID tenantId = TenantContext.require();
        Payment payment = paymentRepository.findByIdAndTenantId(paymentId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND, "Payment not found"));
        return applyReversal(payment, request.amount(), request.reason(), null, request.note())
                .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_FAILED,
                        "Nothing left to reverse on this payment"));
    }

    /**
     * Books a reversal reported by the payment processor.
     *
     * <p>{@code cumulativeAmount} is what the processor says has been returned
     * <em>in total</em> for the payment — Stripe's {@code amount_refunded} is a
     * running total, so booking it verbatim after a first partial refund would
     * reverse the same money twice. Only the delta is recorded.</p>
     *
     * @return empty when the event was already applied or adds nothing
     */
    @Transactional
    public Optional<ReversalResponse> recordExternalReversal(String paymentExternalId,
                                                             BigDecimal cumulativeAmount,
                                                             String currency,
                                                             ReversalReason reason,
                                                             String externalId,
                                                             String note) {
        if (externalId != null && reversalRepository.existsByExternalId(externalId)) {
            return Optional.empty();
        }
        UUID tenantId = TenantContext.require();
        Payment payment = paymentRepository.findByExternalIdAndTenantId(paymentExternalId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND,
                        "No payment recorded for " + paymentExternalId));
        BigDecimal alreadyReversed = reversalRepository.sumByPaymentId(payment.getId());
        BigDecimal delta = cumulativeAmount.subtract(alreadyReversed);
        if (delta.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        return applyReversal(payment, delta, reason, externalId, note);
    }

    private Optional<ReversalResponse> applyReversal(Payment payment, BigDecimal amount,
                                                     ReversalReason reason, String externalId,
                                                     String note) {
        UUID tenantId = TenantContext.require();
        Invoice invoice = invoiceRepository.findByIdAndTenantIdForUpdate(payment.getInvoiceId(), tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND, "Invoice not found"));

        BigDecimal remaining = payment.getAmount().subtract(reversalRepository.sumByPaymentId(payment.getId()));
        if (amount.compareTo(remaining) > 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Cannot reverse %s: only %s of this payment remains".formatted(amount, remaining));
        }

        PaymentReversal reversal = new PaymentReversal();
        reversal.setTenantId(tenantId);
        reversal.setPaymentId(payment.getId());
        reversal.setInvoiceId(invoice.getId());
        reversal.setAmount(amount);
        reversal.setReason(reason);
        reversal.setExternalId(externalId);
        reversal.setNote(note);
        reversal.setCreatedBy(currentUserId());
        PaymentReversal saved = reversalRepository.save(reversal);

        BigDecimal updatedPaid = invoice.getAmountPaid().subtract(amount).max(BigDecimal.ZERO);
        invoice.setAmountPaid(updatedPaid);
        if (invoice.getStatus() == InvoiceStatus.PAID
                && updatedPaid.compareTo(invoice.getTotal()) < 0) {
            // No longer covered, so it owes money again. Set the status
            // directly rather than opening PAID in the transition table —
            // that would also let send() reset a paid invoice.
            invoice.setPaidAt(null);
            invoice.setStatus(reopenedStatus(invoice));
        }
        auditService.record(tenantId, "Invoice", invoice.getId(), AuditAction.UPDATE,
                Map.<String, Object>of(
                        "reversed", amount.toPlainString(),
                        "reason", reason.name(),
                        "amountPaid", updatedPaid.toPlainString()));
        return Optional.of(ReversalResponse.from(saved));
    }

    /** Where an invoice lands when a reversal re-opens it. */
    private InvoiceStatus reopenedStatus(Invoice invoice) {
        if (invoice.getDueDate() != null && invoice.getDueDate().isBefore(LocalDate.now(clock))) {
            return InvoiceStatus.OVERDUE;
        }
        return invoice.getViewedAt() != null ? InvoiceStatus.VIEWED : InvoiceStatus.SENT;
    }

    @Transactional(readOnly = true)
    public List<ReversalResponse> listReversals(UUID invoiceId) {
        UUID tenantId = TenantContext.require();
        return reversalRepository.findByInvoiceIdAndTenantIdOrderByCreatedAtDesc(invoiceId, tenantId)
                .stream().map(ReversalResponse::from).toList();
    }

    private void transitionToPaid(Invoice invoice) {
        invoice.getStatus().requireTransition(invoice.getDocType(), InvoiceStatus.PAID);
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(OffsetDateTime.now(clock));
        auditService.record(invoice.getTenantId(), "Invoice", invoice.getId(),
                AuditAction.STATUS_CHANGE, Map.<String, Object>of("status", "PAID"));
        eventPublisher.publishEvent(new NotificationEvent(invoice.getTenantId(),
                invoice.getCreatedBy(), NotificationType.INVOICE_PAID,
                "Invoice", invoice.getId(), invoice.getInvoiceNumber()));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> list(UUID invoiceId) {
        UUID tenantId = TenantContext.require();
        Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND, "Invoice not found"));
        return paymentRepository
                .findByInvoiceIdAndTenantIdOrderByPaidOnDescCreatedAtDesc(invoice.getId(), tenantId)
                .stream().map(PaymentResponse::from).toList();
    }

    private Invoice loadOpenInvoice(UUID invoiceId) {
        UUID tenantId = TenantContext.require();
        // Row lock: concurrent payments serialize, so the balance check holds.
        Invoice invoice = invoiceRepository.findByIdAndTenantIdForUpdate(invoiceId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND, "Invoice not found"));
        if (invoice.getDocType() != DocType.INVOICE) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Payments only apply to invoices");
        }
        if (!OPEN_STATUSES.contains(invoice.getStatus())) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Payments can only be recorded for sent, viewed, or overdue invoices");
        }
        return invoice;
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal up ? up.userId() : null;
    }
}
