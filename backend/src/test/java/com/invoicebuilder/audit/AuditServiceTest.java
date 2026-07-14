package com.invoicebuilder.audit;

import com.invoicebuilder.auth.UserPrincipal;
import com.invoicebuilder.user.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditLogRepository repository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordCapturesTenantEntityActionAndAuthenticatedUser() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        authenticateAs(userId, tenantId);
        AuditService service = new AuditService(repository);

        service.record(tenantId, "Invoice", entityId, AuditAction.STATUS_CHANGE,
                Map.of("from", "SENT", "to", "PAID"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(saved.getTenantId()).isEqualTo(tenantId);
        org.assertj.core.api.Assertions.assertThat(saved.getUserId()).isEqualTo(userId);
        org.assertj.core.api.Assertions.assertThat(saved.getEntityType()).isEqualTo("Invoice");
        org.assertj.core.api.Assertions.assertThat(saved.getAction()).isEqualTo(AuditAction.STATUS_CHANGE);
        org.assertj.core.api.Assertions.assertThat(saved.getChanges()).containsEntry("to", "PAID");
    }

    @Test
    void recordIsBestEffortWhenRepositoryThrows() {
        UUID tenantId = UUID.randomUUID();
        when(repository.save(org.mockito.ArgumentMatchers.any(AuditLog.class)))
                .thenThrow(new RuntimeException("db down"));
        AuditService service = new AuditService(repository);

        assertThatCode(() -> service.record(tenantId, "Customer", UUID.randomUUID(),
                AuditAction.CREATE, null)).doesNotThrowAnyException();
    }

    @Test
    void forEntityDelegatesToRepository() {
        UUID tenantId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        when(repository.findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                tenantId, "Invoice", entityId)).thenReturn(List.of());
        AuditService service = new AuditService(repository);

        service.forEntity(tenantId, "Invoice", entityId);

        verify(repository).findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                tenantId, "Invoice", entityId);
    }

    private static void authenticateAs(UUID userId, UUID tenantId) {
        UserPrincipal principal = new UserPrincipal(userId, tenantId, "u@x.io", null, Role.OWNER, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
