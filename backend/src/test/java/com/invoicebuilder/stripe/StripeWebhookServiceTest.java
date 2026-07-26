package com.invoicebuilder.stripe;

import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.config.AppProperties;
import com.invoicebuilder.payment.PaymentMethod;
import com.invoicebuilder.payment.PaymentService;
import com.invoicebuilder.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    private static final String SECRET = "whsec_test_secret";
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock private StripeEventRepository stripeEventRepository;
    @Mock private PaymentService paymentService;

    private StripeWebhookService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(null, null, null,
                new AppProperties.Stripe("sk_test_x", SECRET),
                "http://localhost:5173", null, null, null);
        service = new StripeWebhookService(stripeEventRepository, paymentService, properties,
                Clock.fixed(Instant.parse("2026-07-23T10:00:00Z"), ZoneOffset.UTC));
        lenient().when(paymentService.recordExternal(any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    /** A realistic checkout.session.completed body. */
    private static String payload(String eventId, String type, String paymentStatus,
                                  long amountTotal, String currency, String metadata) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "%s",
                  "created": 1785000000,
                  "type": "%s",
                  "data": {
                    "object": {
                      "id": "cs_test_123",
                      "object": "checkout.session",
                      "amount_total": %d,
                      "currency": "%s",
                      "payment_status": "%s",
                      "payment_intent": "pi_test_123",
                      "metadata": %s
                    }
                  }
                }
                """.formatted(eventId, com.stripe.Stripe.API_VERSION, type,
                amountTotal, currency, paymentStatus, metadata);
    }

    private static String validMetadata() {
        return """
                {"invoiceId": "%s", "tenantId": "%s"}""".formatted(INVOICE_ID, TENANT_ID);
    }

    private void handle(String body) {
        service.handle(body, StripeSignatures.header(body, SECRET));
    }

    @Test
    void bookedPaymentUsesTheAmountStripeCollected() {
        String body = payload("evt_1", "checkout.session.completed", "paid", 11900, "eur", validMetadata());
        when(stripeEventRepository.existsById("evt_1")).thenReturn(false);

        handle(body);

        ArgumentCaptor<BigDecimal> amount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(paymentService).recordExternal(eq(INVOICE_ID), amount.capture(),
                eq(PaymentMethod.CARD), eq("pi_test_123"), any());
        assertThat(amount.getValue()).isEqualByComparingTo("119.00");
        verify(stripeEventRepository).save(any(StripeEvent.class));
    }

    @Test
    void zeroDecimalCurrencyIsNotDividedByAHundred() {
        String body = payload("evt_jpy", "checkout.session.completed", "paid", 5000, "jpy", validMetadata());
        when(stripeEventRepository.existsById("evt_jpy")).thenReturn(false);

        handle(body);

        ArgumentCaptor<BigDecimal> amount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(paymentService).recordExternal(any(), amount.capture(), any(), any(), any());
        assertThat(amount.getValue()).isEqualByComparingTo("5000");
    }

    @Test
    void forgedSignatureIsRejectedAndNothingIsBooked() {
        String body = payload("evt_forged", "checkout.session.completed", "paid", 11900, "eur", validMetadata());

        assertThatThrownBy(() -> service.handle(body, StripeSignatures.header(body, "whsec_wrong_secret")))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.handle(body, "t=1,v1=deadbeef"))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.handle(body, null))
                .isInstanceOf(AppException.class);

        verifyNoInteractions(paymentService);
        verify(stripeEventRepository, never()).save(any());
    }

    @Test
    void replayedEventIsIgnored() {
        String body = payload("evt_replay", "checkout.session.completed", "paid", 11900, "eur", validMetadata());
        when(stripeEventRepository.existsById("evt_replay")).thenReturn(true);

        handle(body);

        verifyNoInteractions(paymentService);
        verify(stripeEventRepository, never()).save(any());
    }

    @Test
    void unhandledEventTypesAreIgnored() {
        handle(payload("evt_other", "customer.created", "paid", 11900, "eur", validMetadata()));

        verifyNoInteractions(paymentService);
        verify(stripeEventRepository, never()).save(any());
    }

    @Test
    void sessionCompletedButNotYetPaidIsNotBooked() {
        String body = payload("evt_unpaid", "checkout.session.completed", "unpaid", 11900, "eur", validMetadata());
        when(stripeEventRepository.existsById("evt_unpaid")).thenReturn(false);

        handle(body);

        verifyNoInteractions(paymentService);
        verify(stripeEventRepository, never()).save(any());
    }

    @Test
    void missingOrMalformedMetadataIsNotBooked() {
        String noMeta = payload("evt_nometa", "checkout.session.completed", "paid", 11900, "eur", "{}");
        when(stripeEventRepository.existsById("evt_nometa")).thenReturn(false);
        handle(noMeta);

        String badMeta = payload("evt_badmeta", "checkout.session.completed", "paid", 11900, "eur",
                "{\"invoiceId\": \"not-a-uuid\", \"tenantId\": \"%s\"}".formatted(TENANT_ID));
        when(stripeEventRepository.existsById("evt_badmeta")).thenReturn(false);
        handle(badMeta);

        verifyNoInteractions(paymentService);
        verify(stripeEventRepository, never()).save(any());
    }

    @Test
    void asyncPaymentSucceededIsAlsoBooked() {
        String body = payload("evt_async", "checkout.session.async_payment_succeeded", "paid",
                5000, "eur", validMetadata());
        when(stripeEventRepository.existsById("evt_async")).thenReturn(false);

        handle(body);

        verify(paymentService).recordExternal(eq(INVOICE_ID), any(), eq(PaymentMethod.CARD),
                eq("pi_test_123"), any());
    }

    @Test
    void tenantContextIsClearedAfterBooking() {
        String body = payload("evt_ctx", "checkout.session.completed", "paid", 11900, "eur", validMetadata());
        when(stripeEventRepository.existsById("evt_ctx")).thenReturn(false);
        when(paymentService.recordExternal(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    // The webhook is anonymous: the service must set the tenant itself.
                    assertThat(TenantContext.get()).contains(TENANT_ID);
                    return Optional.empty();
                });

        handle(body);

        assertThat(TenantContext.get()).isEmpty();
    }
}
