package com.invoicebuilder.apikey;

import com.invoicebuilder.apikey.dto.ApiKeyCreatedResponse;
import com.invoicebuilder.apikey.dto.ApiKeyRequest;
import com.invoicebuilder.audit.AuditService;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.tenant.TenantContext;
import com.invoicebuilder.user.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiKeyServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID KEY_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-02T10:00:00Z");

    @Mock private ApiKeyRepository repository;
    @Mock private AuditService auditService;

    private ApiKeyService service;

    @BeforeEach
    void setUp() {
        service = new ApiKeyService(repository, auditService, Clock.fixed(NOW, ZoneOffset.UTC));
        TenantContext.set(TENANT_ID);
        when(repository.save(any(ApiKey.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void theSecretIsReturnedOnceAndOnlyItsHashIsStored() {
        ApiKeyCreatedResponse created = service.create(new ApiKeyRequest("CI pipeline", Role.ADMIN));

        assertThat(created.secret()).startsWith("ib_");
        ArgumentCaptor<ApiKey> saved = ArgumentCaptor.forClass(ApiKey.class);
        org.mockito.Mockito.verify(repository).save(saved.capture());
        // A database leak must not hand over working credentials.
        assertThat(saved.getValue().getKeyHash()).isNotEqualTo(created.secret());
        assertThat(saved.getValue().getKeyHash()).isEqualTo(ApiKeyService.hash(created.secret()));
        // The stored prefix is only enough to recognise a key in a list.
        assertThat(created.secret()).startsWith(saved.getValue().getKeyPrefix());
        assertThat(saved.getValue().getKeyPrefix()).hasSizeLessThan(created.secret().length());
    }

    @Test
    void everyGeneratedKeyIsDistinct() {
        String first = service.create(new ApiKeyRequest("one", Role.ADMIN)).secret();
        String second = service.create(new ApiKeyRequest("two", Role.ADMIN)).secret();

        assertThat(first).isNotEqualTo(second);
    }

    private ApiKey storedKey(String secret) {
        ApiKey key = new ApiKey();
        key.setTenantId(TENANT_ID);
        key.setName("CI");
        key.setKeyHash(ApiKeyService.hash(secret));
        key.setKeyPrefix(secret.substring(0, 11));
        key.setRole(Role.ADMIN);
        key.setCreatedBy(UUID.randomUUID());
        org.springframework.test.util.ReflectionTestUtils.setField(key, "id", KEY_ID);
        return key;
    }

    @Test
    void aValidKeyResolvesToItsTenantAndRole() {
        String secret = "ib_abcdefghijklmnop";
        when(repository.findByKeyHash(ApiKeyService.hash(secret))).thenReturn(Optional.of(storedKey(secret)));

        Optional<ApiKey> resolved = service.authenticate(secret);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(resolved.get().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void aRevokedKeyStopsWorkingImmediately() {
        String secret = "ib_revokedkey12345";
        ApiKey key = storedKey(secret);
        key.setRevokedAt(OffsetDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)).minusMinutes(1));
        when(repository.findByKeyHash(ApiKeyService.hash(secret))).thenReturn(Optional.of(key));

        assertThat(service.authenticate(secret)).isEmpty();
    }

    @Test
    void unknownOrMalformedSecretsAreRejectedWithoutTouchingTheDatabase() {
        when(repository.findByKeyHash(any())).thenReturn(Optional.empty());

        assertThat(service.authenticate("ib_nosuchkey")).isEmpty();
        assertThat(service.authenticate(null)).isEmpty();
        assertThat(service.authenticate("")).isEmpty();
        // Anything without our prefix cannot be one of ours.
        assertThat(service.authenticate("Bearer eyJhbGci")).isEmpty();
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(1)).findByKeyHash(any());
    }

    @Test
    void lastUsedIsStampedButNotOnEverySingleRequest() {
        String secret = "ib_busykey123456789";
        ApiKey key = storedKey(secret);
        when(repository.findByKeyHash(ApiKeyService.hash(secret))).thenReturn(Optional.of(key));

        service.authenticate(secret);
        assertThat(key.getLastUsedAt()).isNotNull();

        // A write per request would make every API call a database write.
        OffsetDateTime stamped = key.getLastUsedAt();
        service.authenticate(secret);
        assertThat(key.getLastUsedAt()).isEqualTo(stamped);
    }

    @Test
    void revokingIsIdempotentAndScopedToTheTenant() {
        ApiKey key = storedKey("ib_tobeRevoked1234");
        when(repository.findByIdAndTenantId(KEY_ID, TENANT_ID)).thenReturn(Optional.of(key));

        service.revoke(KEY_ID);
        OffsetDateTime first = key.getRevokedAt();
        assertThat(first).isNotNull();

        service.revoke(KEY_ID);
        assertThat(key.getRevokedAt()).isEqualTo(first);
    }

    @Test
    void revokingSomethingFromAnotherTenantFails() {
        when(repository.findByIdAndTenantId(KEY_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(KEY_ID)).isInstanceOf(AppException.class);
    }
}
