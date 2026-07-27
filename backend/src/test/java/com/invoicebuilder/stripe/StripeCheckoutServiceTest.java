package com.invoicebuilder.stripe;

import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.config.AppProperties;
import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceRepository;
import com.invoicebuilder.invoice.InvoiceStatus;
import com.stripe.StripeClient;
import com.stripe.exception.ApiException;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the double-charge guards. Each test here fails if the corresponding
 * guard is removed — the point the review made about the earlier tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StripeCheckoutServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final String TOKEN = "tok_public";
    private static final Instant NOW = Instant.parse("2026-07-27T10:00:00Z");

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private StripeCheckoutSessionRepository sessionRepository;

    private StripeClient client;
    private StripeCheckoutService service;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        client = mock(StripeClient.class, RETURNS_DEEP_STUBS);
        AppProperties properties = new AppProperties(null, null, null,
                new AppProperties.Stripe("sk_test_x", "whsec_x"),
                "http://localhost:5173", null, null, null);
        service = new StripeCheckoutService(invoiceRepository, customerRepository, sessionRepository,
                properties, Clock.fixed(NOW, ZoneOffset.UTC), client);

        invoice = new Invoice();
        invoice.setTenantId(TENANT_ID);
        invoice.setCustomerId(UUID.randomUUID());
        invoice.setInvoiceNumber("INV-2026-0001");
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setCurrency("EUR");
        invoice.setTotal(new BigDecimal("119.00"));
        invoice.setAmountPaid(BigDecimal.ZERO);
        org.springframework.test.util.ReflectionTestUtils.setField(invoice, "id", INVOICE_ID);
        when(invoiceRepository.findByPublicToken(TOKEN)).thenReturn(Optional.of(invoice));
        when(customerRepository.findById(any())).thenReturn(Optional.empty());
    }

    private StripeCheckoutSession storedSession(String id, long amountMinor) {
        return new StripeCheckoutSession(id, TENANT_ID, INVOICE_ID, amountMinor, "eur",
                "https://checkout.stripe.test/" + id,
                OffsetDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)).plusHours(12),
                OffsetDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private void stubLiveSession(String status) throws Exception {
        Session live = mock(Session.class);
        when(live.getStatus()).thenReturn(status);
        when(client.v1().checkout().sessions().retrieve(any(String.class))).thenReturn(live);
    }

    @Test
    void reusesAnOpenSessionForTheSameAmountInsteadOfMintingASecond() throws Exception {
        when(sessionRepository.findByInvoiceIdAndExpiresAtAfterOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of(storedSession("cs_open", 11900)));
        stubLiveSession("open");

        String url = service.createCheckoutUrl(TOKEN);

        assertThat(url).isEqualTo("https://checkout.stripe.test/cs_open");
        verify(client.v1().checkout().sessions(), never()).create(any(), any());
    }

    @Test
    void refusesWhenAnyLiveSessionIsAlreadyComplete() throws Exception {
        // The stored session was for the FULL balance; a partial payment has
        // since changed what is owed. The completed session must still block
        // a second charge — the amount filter must not hide it.
        invoice.setAmountPaid(new BigDecimal("30.00"));
        when(sessionRepository.findByInvoiceIdAndExpiresAtAfterOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of(storedSession("cs_done", 11900)));
        stubLiveSession("complete");

        assertThatThrownBy(() -> service.createCheckoutUrl(TOKEN))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("already being processed");
        verify(client.v1().checkout().sessions(), never()).create(any(), any());
    }

    @Test
    void failsClosedWhenStripeCannotBeReached() throws Exception {
        // Guessing wrong here charges someone twice, so an unreadable session
        // must refuse rather than fall through to creating a new one.
        when(sessionRepository.findByInvoiceIdAndExpiresAtAfterOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of(storedSession("cs_unknown", 11900)));
        when(client.v1().checkout().sessions().retrieve(any(String.class)))
                .thenThrow(new ApiException("stripe down", null, null, 500, null));

        assertThatThrownBy(() -> service.createCheckoutUrl(TOKEN)).isInstanceOf(AppException.class);
        verify(client.v1().checkout().sessions(), never()).create(any(), any());
    }

    @Test
    void newSessionCarriesADeterministicIdempotencyKeySoConcurrentClicksCollapse() throws Exception {
        when(sessionRepository.findByInvoiceIdAndExpiresAtAfterOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of());
        Session created = mock(Session.class);
        when(created.getId()).thenReturn("cs_new");
        when(created.getUrl()).thenReturn("https://checkout.stripe.test/cs_new");
        when(created.getExpiresAt()).thenReturn(NOW.getEpochSecond() + 86400);
        when(client.v1().checkout().sessions().create(any(), any(com.stripe.net.RequestOptions.class)))
                .thenReturn(created);

        String url = service.createCheckoutUrl(TOKEN);

        assertThat(url).isEqualTo("https://checkout.stripe.test/cs_new");
        org.mockito.ArgumentCaptor<com.stripe.net.RequestOptions> options =
                org.mockito.ArgumentCaptor.forClass(com.stripe.net.RequestOptions.class);
        verify(client.v1().checkout().sessions()).create(any(), options.capture());
        // Same invoice + amount + currency => same key => Stripe returns one
        // session even if two clicks race.
        assertThat(options.getValue().getIdempotencyKey())
                .isEqualTo("checkout:%s:11900:eur".formatted(INVOICE_ID));
        verify(sessionRepository).save(any(StripeCheckoutSession.class));
    }
}
