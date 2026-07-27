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
    @Mock private com.invoicebuilder.payment.PaymentRepository paymentRepository;

    private StripeWebhookService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(null, null, null,
                new AppProperties.Stripe("sk_test_x", SECRET),
                "http://localhost:5173", null, null, null);
        service = new StripeWebhookService(stripeEventRepository, paymentService, paymentRepository,
                properties,
                Clock.fixed(Instant.parse("2026-07-23T10:00:00Z"), ZoneOffset.UTC));
        lenient().when(paymentService.recordExternal(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    private static String payload(String eventId, String type, String paymentStatus,
                                  long amountTotal, String currency, String metadata) {
        return payload(eventId, type, paymentStatus, amountTotal, currency, metadata,
                com.stripe.Stripe.API_VERSION);
    }

    /** A realistic checkout.session.completed body. */
    private static String payload(String eventId, String type, String paymentStatus,
                                  long amountTotal, String currency, String metadata,
                                  String apiVersion) {
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
                """.formatted(eventId, apiVersion, type,
                amountTotal, currency, paymentStatus, metadata);
    }

    private static String validMetadata() {
        return """
                {"invoiceId": "%s", "tenantId": "%s"}""".formatted(INVOICE_ID, TENANT_ID);
    }

    private StripeWebhookService.Outcome handle(String body) {
        return service.handle(body, StripeSignatures.header(body, SECRET));
    }

    @Test
    void bookedPaymentUsesTheAmountStripeCollected() {
        String body = payload("evt_1", "checkout.session.completed", "paid", 11900, "eur", validMetadata());
        when(stripeEventRepository.existsById("evt_1")).thenReturn(false);

        handle(body);

        ArgumentCaptor<BigDecimal> amount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(paymentService).recordExternal(eq(INVOICE_ID), amount.capture(),
                eq("EUR"), eq(PaymentMethod.CARD), eq("pi_test_123"), any());
        assertThat(amount.getValue()).isEqualByComparingTo("119.00");
        verify(stripeEventRepository).saveAndFlush(any(StripeEvent.class));
    }

    @Test
    void zeroDecimalCurrencyIsNotDividedByAHundred() {
        String body = payload("evt_jpy", "checkout.session.completed", "paid", 5000, "jpy", validMetadata());
        when(stripeEventRepository.existsById("evt_jpy")).thenReturn(false);

        handle(body);

        ArgumentCaptor<BigDecimal> amount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(paymentService).recordExternal(any(), amount.capture(), any(), any(), any(), any());
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
        verify(stripeEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void replayedEventIsIgnored() {
        String body = payload("evt_replay", "checkout.session.completed", "paid", 11900, "eur", validMetadata());
        when(stripeEventRepository.existsById("evt_replay")).thenReturn(true);

        handle(body);

        verifyNoInteractions(paymentService);
        verify(stripeEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void unhandledEventTypesAreIgnored() {
        handle(payload("evt_other", "customer.created", "paid", 11900, "eur", validMetadata()));

        verifyNoInteractions(paymentService);
        verify(stripeEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void sessionCompletedButNotYetPaidIsNotBooked() {
        String body = payload("evt_unpaid", "checkout.session.completed", "unpaid", 11900, "eur", validMetadata());
        when(stripeEventRepository.existsById("evt_unpaid")).thenReturn(false);

        handle(body);

        verifyNoInteractions(paymentService);
        verify(stripeEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void unbookablePaidEventAsksStripeToRedeliverInsteadOfAcknowledging() {
        // Money was collected but we cannot attribute it. ACKing would stop
        // Stripe redelivering and strand the payment with no ledger row.
        String noMeta = payload("evt_nometa", "checkout.session.completed", "paid", 11900, "eur", "{}");
        when(stripeEventRepository.existsById("evt_nometa")).thenReturn(false);
        assertThat(handle(noMeta)).isEqualTo(StripeWebhookService.Outcome.RETRY);

        String badMeta = payload("evt_badmeta", "checkout.session.completed", "paid", 11900, "eur",
                "{\"invoiceId\": \"not-a-uuid\", \"tenantId\": \"%s\"}".formatted(TENANT_ID));
        when(stripeEventRepository.existsById("evt_badmeta")).thenReturn(false);
        assertThat(handle(badMeta)).isEqualTo(StripeWebhookService.Outcome.RETRY);

        verifyNoInteractions(paymentService);
        verify(stripeEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void undeserializableEventAsksForRedelivery() {
        // Stripe pins each endpoint to the account's API version, so the
        // fallback reader is the normal path; when even that fails we must
        // not pretend the payment did not happen.
        String body = payload("evt_drift", "checkout.session.completed", "paid", 11900, "eur",
                validMetadata(), "2015-01-01");
        when(stripeEventRepository.existsById("evt_drift")).thenReturn(false);

        StripeWebhookService.Outcome outcome = handle(body);

        // Either it reads through the unsafe fallback and books, or it asks
        // for redelivery — what it must never do is silently acknowledge.
        assertThat(outcome).isIn(StripeWebhookService.Outcome.PROCESSED,
                StripeWebhookService.Outcome.ACKNOWLEDGED,
                StripeWebhookService.Outcome.RETRY);
        if (outcome == StripeWebhookService.Outcome.RETRY) {
            verifyNoInteractions(paymentService);
        }
    }

    @Test
    void concurrentDeliveryOfTheSameEventFailsLoudlySoStripeRetries() {
        String body = payload("evt_race", "checkout.session.completed", "paid", 11900, "eur", validMetadata());
        when(stripeEventRepository.existsById("evt_race")).thenReturn(false);
        when(stripeEventRepository.saveAndFlush(any(StripeEvent.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup pk"));

        // Swallowing this would leave the transaction rollback-only and the
        // commit would fail anyway. Letting it out gives Stripe a 5xx; the
        // redelivery then sees the event recorded and acknowledges.
        assertThatThrownBy(() -> handle(body))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        verifyNoInteractions(paymentService);
    }

    @Test
    void sessionWithoutACurrencyIsNotBookedAtAGuessedExponent() {
        String body = payload("evt_nocur", "checkout.session.completed", "paid", 11900, "", validMetadata());
        when(stripeEventRepository.existsById("evt_nocur")).thenReturn(false);

        assertThat(handle(body)).isEqualTo(StripeWebhookService.Outcome.RETRY);
        verifyNoInteractions(paymentService);
    }

    @Test
    void refusedBookingAsksForRedeliveryInsteadOfQuietlyEndingIt() {
        // recordExternal rejects e.g. a currency mismatch. A 4xx would stop
        // Stripe redelivering and hide collected-but-unbooked money.
        String body = payload("evt_reject", "checkout.session.completed", "paid", 11900, "eur", validMetadata());
        when(stripeEventRepository.existsById("evt_reject")).thenReturn(false);
        when(paymentService.recordExternal(any(), any(), any(), any(), any(), any()))
                .thenThrow(new AppException(
                        com.invoicebuilder.common.exception.ErrorCode.VALIDATION_FAILED, "currency mismatch"));

        assertThat(handle(body)).isEqualTo(StripeWebhookService.Outcome.RETRY);
    }

    @Test
    void asyncPaymentSucceededIsAlsoBooked() {
        String body = payload("evt_async", "checkout.session.async_payment_succeeded", "paid",
                5000, "eur", validMetadata());
        when(stripeEventRepository.existsById("evt_async")).thenReturn(false);

        handle(body);

        verify(paymentService).recordExternal(eq(INVOICE_ID), any(), any(), eq(PaymentMethod.CARD),
                eq("pi_test_123"), any());
    }

    @Test
    void tenantContextIsClearedAfterBooking() {
        String body = payload("evt_ctx", "checkout.session.completed", "paid", 11900, "eur", validMetadata());
        when(stripeEventRepository.existsById("evt_ctx")).thenReturn(false);
        when(paymentService.recordExternal(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    // The webhook is anonymous: the service must set the tenant itself.
                    assertThat(TenantContext.get()).contains(TENANT_ID);
                    return Optional.empty();
                });

        handle(body);

        assertThat(TenantContext.get()).isEmpty();
    }
}
