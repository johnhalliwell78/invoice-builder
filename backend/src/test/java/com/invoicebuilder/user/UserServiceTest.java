package com.invoicebuilder.user;

import com.invoicebuilder.auth.RefreshTokenService;
import com.invoicebuilder.auth.UserPrincipal;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.config.AppProperties;
import com.invoicebuilder.email.EmailService;
import com.invoicebuilder.tenant.Tenant;
import com.invoicebuilder.tenant.TenantContext;
import com.invoicebuilder.tenant.TenantRepository;
import com.invoicebuilder.user.dto.AcceptInviteRequest;
import com.invoicebuilder.user.dto.ChangeRoleRequest;
import com.invoicebuilder.user.dto.InviteRequest;
import com.invoicebuilder.user.dto.MemberResponse;
import com.invoicebuilder.user.dto.SetActiveRequest;
import com.invoicebuilder.user.dto.TransferOwnershipRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-11T12:00:00Z");

    @Mock private AppUserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private MessageSource messages;

    private UserService service;
    private AppUser actor;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("0123456789abcdef0123456789abcdef", Duration.ofMinutes(15), Duration.ofDays(7), "test"),
                new AppProperties.OAuth2("http://localhost:5173/auth/oauth2/callback", "http://localhost:5173/login"),
                new AppProperties.Sendgrid("", "noreply@test.local", "Test"),
                new AppProperties.Storage(Path.of("./build/tmp"), Path.of("./build/tmp")),
                new AppProperties.Cors(List.of()),
                new AppProperties.RateLimit(5, Duration.ofMinutes(15), 100));
        service = new UserService(userRepository, tenantRepository, passwordEncoder,
                emailService, refreshTokenService, messages, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        TenantContext.set(TENANT_ID);
        actor = user(ACTOR_ID, "owner@acme.example", Role.OWNER, true);
        authenticateAs(actor);

        tenant = new Tenant();
        tenant.setName("Acme GmbH");
        tenant.setDefaultLocale("en");

        lenient().when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
        lenient().when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        lenient().when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(messages.getMessage(eq("email.invite.subject"), any(), any(Locale.class)))
                .thenReturn("Invite subject");
        lenient().when(messages.getMessage(eq("email.invite.body"), any(), any(Locale.class)))
                .thenReturn("Invite body");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static AppUser user(UUID id, String email, Role role, boolean active) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setTenantId(TENANT_ID);
        u.setEmail(email);
        u.setFullName(email.substring(0, email.indexOf('@')));
        u.setRole(role);
        u.setActive(active);
        return u;
    }

    private static void authenticateAs(AppUser user) {
        UserPrincipal principal = new UserPrincipal(
                user.getId(), TENANT_ID, user.getEmail(), null, user.getRole(), true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    void inviteCreatesInactiveUserWithTokenAndSendsEmail() {
        when(userRepository.existsByEmail("new@acme.example")).thenReturn(false);

        MemberResponse response = service.invite(new InviteRequest("new@acme.example", "MEMBER"));

        assertThat(response.active()).isFalse();
        assertThat(response.pendingInvite()).isTrue();
        assertThat(response.role()).isEqualTo(Role.MEMBER);

        ArgumentCaptor<EmailService.EmailMessage> captor =
                ArgumentCaptor.forClass(EmailService.EmailMessage.class);
        verify(emailService).send(captor.capture());
        assertThat(captor.getValue().toEmail()).isEqualTo("new@acme.example");
    }

    @Test
    void inviteRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("owner@acme.example")).thenReturn(true);

        assertThatThrownBy(() -> service.invite(new InviteRequest("owner@acme.example", "MEMBER")))
                .isInstanceOf(AppException.class);
        verifyNoInteractions(emailService);
    }

    @Test
    void changeRoleRejectsSelfAndOwnerTarget() {
        when(userRepository.findByIdAndTenantId(ACTOR_ID, TENANT_ID)).thenReturn(Optional.of(actor));
        assertThatThrownBy(() -> service.changeRole(ACTOR_ID, new ChangeRoleRequest("ADMIN")))
                .isInstanceOf(AppException.class);

        AppUser otherOwner = user(UUID.randomUUID(), "boss@acme.example", Role.OWNER, true);
        when(userRepository.findByIdAndTenantId(otherOwner.getId(), TENANT_ID))
                .thenReturn(Optional.of(otherOwner));
        assertThatThrownBy(() -> service.changeRole(otherOwner.getId(), new ChangeRoleRequest("MEMBER")))
                .isInstanceOf(AppException.class);
    }

    @Test
    void changeRolePromotesMemberToAdmin() {
        AppUser member = user(UUID.randomUUID(), "member@acme.example", Role.MEMBER, true);
        when(userRepository.findByIdAndTenantId(member.getId(), TENANT_ID))
                .thenReturn(Optional.of(member));

        MemberResponse response = service.changeRole(member.getId(), new ChangeRoleRequest("ADMIN"));

        assertThat(response.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void deactivateRevokesRefreshTokensAndProtectsSelfAndOwner() {
        AppUser member = user(UUID.randomUUID(), "member@acme.example", Role.MEMBER, true);
        when(userRepository.findByIdAndTenantId(member.getId(), TENANT_ID))
                .thenReturn(Optional.of(member));

        service.setActive(member.getId(), new SetActiveRequest(false));

        assertThat(member.isActive()).isFalse();
        verify(refreshTokenService).revokeAllForUser(member.getId());

        when(userRepository.findByIdAndTenantId(ACTOR_ID, TENANT_ID)).thenReturn(Optional.of(actor));
        assertThatThrownBy(() -> service.setActive(ACTOR_ID, new SetActiveRequest(false)))
                .isInstanceOf(AppException.class);
    }

    @Test
    void transferOwnershipSwapsRoles() {
        AppUser admin = user(UUID.randomUUID(), "admin@acme.example", Role.ADMIN, true);
        when(userRepository.findByIdAndTenantId(admin.getId(), TENANT_ID))
                .thenReturn(Optional.of(admin));

        service.transferOwnership(new TransferOwnershipRequest(admin.getId()));

        assertThat(actor.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getRole()).isEqualTo(Role.OWNER);
    }

    @Test
    void transferOwnershipRejectsInactiveTarget() {
        AppUser inactive = user(UUID.randomUUID(), "gone@acme.example", Role.MEMBER, false);
        when(userRepository.findByIdAndTenantId(inactive.getId(), TENANT_ID))
                .thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.transferOwnership(new TransferOwnershipRequest(inactive.getId())))
                .isInstanceOf(AppException.class);
        assertThat(actor.getRole()).isEqualTo(Role.OWNER);
    }

    @Test
    void acceptInviteActivatesUserAndClearsToken() {
        when(userRepository.existsByEmail("new@acme.example")).thenReturn(false);
        MemberResponse invited = service.invite(new InviteRequest("new@acme.example", "MEMBER"));

        // Capture the raw token from the emailed link.
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(messages).getMessage(eq("email.invite.body"), argsCaptor.capture(), any(Locale.class));
        String link = (String) argsCaptor.getValue()[2];
        String rawToken = link.substring(link.lastIndexOf('/') + 1);

        AppUser pending = user(invited.id(), "new@acme.example", Role.MEMBER, false);
        pending.setInvitedAt(OffsetDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)));
        pending.setInviteTokenHash(hashOf(rawToken));
        when(userRepository.findByInviteTokenHash(hashOf(rawToken))).thenReturn(Optional.of(pending));
        when(passwordEncoder.encode("Sup3rSecret")).thenReturn("bcrypt-hash");

        service.acceptInvite(rawToken, new AcceptInviteRequest("New Member", "Sup3rSecret"));

        assertThat(pending.isActive()).isTrue();
        assertThat(pending.getInviteTokenHash()).isNull();
        assertThat(pending.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(pending.getFullName()).isEqualTo("New Member");
    }

    @Test
    void acceptInviteRejectsExpiredToken() {
        AppUser pending = user(UUID.randomUUID(), "old@acme.example", Role.MEMBER, false);
        pending.setInvitedAt(OffsetDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)).minusDays(8));
        pending.setInviteTokenHash(hashOf("stale-token"));
        when(userRepository.findByInviteTokenHash(hashOf("stale-token"))).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.acceptInvite("stale-token",
                new AcceptInviteRequest("Late Person", "Sup3rSecret")))
                .isInstanceOf(AppException.class);
        assertThat(pending.isActive()).isFalse();
    }

    private static String hashOf(String raw) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
