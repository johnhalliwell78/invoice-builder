package com.invoicebuilder.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicebuilder.audit.AuditAction;
import com.invoicebuilder.audit.AuditService;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.notification.NotificationEvent;
import com.invoicebuilder.tenant.TenantContext;
import com.invoicebuilder.webhook.dto.WebhookEndpointCreatedResponse;
import com.invoicebuilder.webhook.dto.WebhookEndpointRequest;
import com.invoicebuilder.webhook.dto.WebhookEndpointResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public WebhookService(WebhookEndpointRepository endpointRepository,
                          WebhookDeliveryRepository deliveryRepository,
                          AuditService auditService,
                          ObjectMapper objectMapper,
                          Clock clock) {
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public WebhookEndpointCreatedResponse create(WebhookEndpointRequest request) {
        UUID tenantId = TenantContext.require();
        WebhookUrlValidator.requireAllowed(request.url());

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setTenantId(tenantId);
        endpoint.setUrl(request.url().trim());
        endpoint.setSecret(generateSecret());
        endpoint.setEventTypes(List.copyOf(request.eventTypes()));
        endpoint.setActive(true);
        WebhookEndpoint saved = endpointRepository.save(endpoint);

        auditService.record(tenantId, "WebhookEndpoint", saved.getId(), AuditAction.CREATE,
                Map.<String, Object>of("url", saved.getUrl()));
        // The secret is needed by the receiver to verify signatures, so unlike
        // an API key it stays retrievable — it authenticates us to them, not
        // them to us.
        return new WebhookEndpointCreatedResponse(WebhookEndpointResponse.from(saved), saved.getSecret());
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpointResponse> list() {
        return endpointRepository.findByTenantIdOrderByCreatedAtDesc(TenantContext.require())
                .stream().map(WebhookEndpointResponse::from).toList();
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.require();
        WebhookEndpoint endpoint = endpointRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Webhook endpoint not found"));
        auditService.record(tenantId, "WebhookEndpoint", endpoint.getId(), AuditAction.DELETE, null);
        endpointRepository.delete(endpoint);
    }

    /**
     * Queues deliveries for subscribed endpoints once the originating
     * transaction has committed — the same AFTER_COMMIT discipline the
     * notification listener uses, so a rolled-back invoice never announces
     * itself to the outside world.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onDomainEvent(NotificationEvent event) {
        try {
            List<WebhookEndpoint> endpoints =
                    endpointRepository.findByTenantIdAndActiveTrue(event.tenantId());
            for (WebhookEndpoint endpoint : endpoints) {
                if (!endpoint.getEventTypes().contains(event.type())) {
                    continue;
                }
                WebhookDelivery delivery = new WebhookDelivery();
                delivery.setTenantId(event.tenantId());
                delivery.setEndpointId(endpoint.getId());
                delivery.setEventType(event.type().name());
                delivery.setPayload(serialise(event));
                delivery.setNextAttemptAt(OffsetDateTime.now(clock));
                delivery.setCreatedAt(OffsetDateTime.now(clock));
                deliveryRepository.save(delivery);
            }
        } catch (RuntimeException e) {
            // Never let webhook bookkeeping break the business flow that
            // triggered it; the event has already been committed.
            log.error("Could not queue webhook deliveries for {}", event.type(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<WebhookDelivery> recentDeliveries() {
        return deliveryRepository.findByTenantIdOrderByCreatedAtDesc(TenantContext.require(), Limit.of(50));
    }

    private String serialise(NotificationEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", event.type().name());
        body.put("occurredAt", OffsetDateTime.now(clock).toString());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("referenceType", event.referenceType());
        data.put("referenceId", event.referenceId() == null ? null : event.referenceId().toString());
        data.put("subject", event.subject());
        body.put("data", data);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise webhook payload", e);
        }
    }

    private static String generateSecret() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
