package com.invoicebuilder.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository repository;
    @InjectMocks private NotificationService service;

    @Test
    void createPersistsNotificationForRecipient() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(new NotificationEvent(tenantId, userId, NotificationType.INVOICE_PAID,
                "Invoice", invoiceId, "INV-2026-0001"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getType()).isEqualTo(NotificationType.INVOICE_PAID);
        assertThat(saved.getTitle()).isEqualTo("Invoice paid");
        assertThat(saved.getMessage()).isEqualTo("INV-2026-0001");
        assertThat(saved.getReferenceId()).isEqualTo(invoiceId);
    }

    @Test
    void createSkipsWhenNoRecipient() {
        service.create(new NotificationEvent(UUID.randomUUID(), null, NotificationType.INVOICE_VIEWED,
                "Invoice", UUID.randomUUID(), "INV-2026-0002"));

        verify(repository, never()).save(any());
    }

    @Test
    void markReadFlipsTheFlag() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Notification n = new Notification();
        n.setRead(false);
        when(repository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(n));

        service.markRead(id, userId);

        assertThat(n.isRead()).isTrue();
    }

    @Test
    void markReadIsNoOpWhenNotFound() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(repository.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        service.markRead(id, userId);  // must not throw
    }

    @Test
    void unreadCountDelegatesToRepository() {
        UUID userId = UUID.randomUUID();
        when(repository.countByUserIdAndReadIsFalse(userId)).thenReturn(3L);

        assertThat(service.unreadCount(userId)).isEqualTo(3L);
    }
}
