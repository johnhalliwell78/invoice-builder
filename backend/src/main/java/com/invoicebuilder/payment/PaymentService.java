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
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Set<InvoiceStatus> OPEN_STATUSES =
            EnumSet.of(InvoiceStatus.SENT, InvoiceStatus.VIEWED, InvoiceStatus.OVERDUE);

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PaymentService(PaymentRepository paymentRepository,
                          InvoiceRepository invoiceRepository,
                          AuditService auditService,
                          ApplicationEventPublisher eventPublisher,
                          Clock clock) {
        this.paymentRepository = paymentRepository;
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
            invoice.getStatus().requireTransition(invoice.getDocType(), InvoiceStatus.PAID);
            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setPaidAt(OffsetDateTime.now(clock));
            auditService.record(invoice.getTenantId(), "Invoice", invoice.getId(),
                    AuditAction.STATUS_CHANGE, Map.<String, Object>of("status", "PAID"));
            eventPublisher.publishEvent(new NotificationEvent(invoice.getTenantId(),
                    invoice.getCreatedBy(), NotificationType.INVOICE_PAID,
                    "Invoice", invoice.getId(), invoice.getInvoiceNumber()));
        } else {
            auditService.record(invoice.getTenantId(), "Invoice", invoice.getId(),
                    AuditAction.UPDATE, Map.<String, Object>of(
                            "paymentAmount", request.amount().toPlainString(),
                            "amountPaid", invoice.getAmountPaid().toPlainString()));
        }
        return PaymentResponse.from(saved);
    }

    /** The former "mark paid": records the whole remaining balance as one payment. */
    @Transactional
    public PaymentResponse markRemainingPaid(UUID invoiceId) {
        Invoice invoice = loadOpenInvoice(invoiceId);
        BigDecimal balance = invoice.getTotal().subtract(invoice.getAmountPaid());
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Invoice has no remaining balance");
        }
        return record(invoiceId, new PaymentRequest(balance, PaymentMethod.OTHER, null, null));
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
        Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
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
