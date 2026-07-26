package com.invoicebuilder.stripe;

import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.config.AppProperties;
import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.invoice.DocType;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceRepository;
import com.invoicebuilder.invoice.InvoiceStatus;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Creates Stripe Checkout Sessions for invoice recipients. The hosted page
 * means no card data reaches this application.
 */
@Service
public class StripeCheckoutService {

    private static final Logger log = LoggerFactory.getLogger(StripeCheckoutService.class);

    private static final Set<InvoiceStatus> PAYABLE =
            EnumSet.of(InvoiceStatus.SENT, InvoiceStatus.VIEWED, InvoiceStatus.OVERDUE);

    static final String METADATA_INVOICE_ID = "invoiceId";
    static final String METADATA_TENANT_ID = "tenantId";

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final AppProperties appProperties;

    public StripeCheckoutService(InvoiceRepository invoiceRepository,
                                 CustomerRepository customerRepository,
                                 AppProperties appProperties) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.appProperties = appProperties;
    }

    public boolean enabled() {
        return appProperties.stripe() != null && appProperties.stripe().enabled();
    }

    /**
     * Creates a Checkout Session for the invoice's remaining balance and
     * returns the hosted payment URL.
     */
    @Transactional(readOnly = true)
    public String createCheckoutUrl(String publicToken) {
        if (!enabled()) {
            throw new AppException(ErrorCode.INVOICE_NOT_FOUND, "Online payment is not enabled");
        }
        Invoice invoice = invoiceRepository.findByPublicToken(publicToken)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND, "Invoice not found"));
        BigDecimal balance = payableBalance(invoice);

        String currency = invoice.getCurrency().toLowerCase(Locale.ROOT);
        long amount = MoneyUnits.toMinorUnits(balance, invoice.getCurrency());
        String baseUrl = trimTrailingSlash(appProperties.publicBaseUrl());
        String returnUrl = baseUrl + "/i/" + publicToken;

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(returnUrl + "?payment=success")
                .setCancelUrl(returnUrl + "?payment=cancelled")
                .setClientReferenceId(invoice.getId().toString())
                .setCustomerEmail(recipientEmail(invoice))
                .putMetadata(METADATA_INVOICE_ID, invoice.getId().toString())
                .putMetadata(METADATA_TENANT_ID, invoice.getTenantId().toString())
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .putMetadata(METADATA_INVOICE_ID, invoice.getId().toString())
                        .putMetadata(METADATA_TENANT_ID, invoice.getTenantId().toString())
                        .build())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currency)
                                .setUnitAmount(amount)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(invoice.getInvoiceNumber())
                                        .build())
                                .build())
                        .build())
                .build();

        try {
            Session session = client().v1().checkout().sessions().create(params);
            return session.getUrl();
        } catch (StripeException e) {
            log.error("Stripe checkout session creation failed for invoice {}", invoice.getId(), e);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Could not start the payment");
        }
    }

    /** Whether this invoice could be paid online right now (drives the UI button). */
    public boolean payableNow(Invoice invoice) {
        if (!enabled() || invoice.getDocType() != DocType.INVOICE
                || !PAYABLE.contains(invoice.getStatus())) {
            return false;
        }
        return invoice.getTotal().subtract(invoice.getAmountPaid()).compareTo(BigDecimal.ZERO) > 0;
    }

    /** Validates the invoice is payable and returns what is still owed. */
    private BigDecimal payableBalance(Invoice invoice) {
        if (invoice.getDocType() != DocType.INVOICE) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION, "Estimates cannot be paid");
        }
        if (!PAYABLE.contains(invoice.getStatus())) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION, "Invoice is not payable");
        }
        BigDecimal balance = invoice.getTotal().subtract(invoice.getAmountPaid());
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION, "Invoice has no remaining balance");
        }
        return balance;
    }

    private String recipientEmail(Invoice invoice) {
        return customerRepository.findById(invoice.getCustomerId())
                .map(Customer::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .orElse(null);
    }

    private StripeClient client() {
        return new StripeClient(appProperties.stripe().secretKey());
    }

    private static String trimTrailingSlash(String url) {
        String value = url == null ? "" : url;
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
