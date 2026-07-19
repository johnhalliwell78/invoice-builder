package com.invoicebuilder.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Persists in-app notifications and serves them per user. Notifications are
 * created from {@link NotificationEvent}s published by services; the listener
 * runs after the originating transaction commits (so nothing is announced for
 * an action that rolled back) in its own transaction.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNotificationEvent(NotificationEvent event) {
        create(event);
    }

    /** Persists a notification for the event's recipient, if there is one. */
    public Notification create(NotificationEvent event) {
        if (event.userId() == null) {
            return null;
        }
        Notification notification = new Notification();
        notification.setTenantId(event.tenantId());
        notification.setUserId(event.userId());
        notification.setType(event.type());
        notification.setTitle(event.type().defaultTitle());
        notification.setMessage(event.subject());
        notification.setReferenceType(event.referenceType());
        notification.setReferenceId(event.referenceId());
        Notification saved = repository.save(notification);
        log.debug("Notification {} created for user {}", event.type(), event.userId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(UUID userId, Pageable pageable) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndReadIsFalse(userId);
    }

    @Transactional
    public void markRead(UUID id, UUID userId) {
        // Lenient: marking an already-gone or foreign notification read is a no-op.
        repository.findByIdAndUserId(id, userId).ifPresent(n -> n.setRead(true));
    }

    @Transactional
    public void markAllRead(UUID userId) {
        repository.markAllRead(userId);
    }
}
