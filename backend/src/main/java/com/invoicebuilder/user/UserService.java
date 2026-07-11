package com.invoicebuilder.user;

import com.invoicebuilder.auth.RefreshTokenService;
import com.invoicebuilder.auth.UserPrincipal;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.config.AppProperties;
import com.invoicebuilder.email.EmailService;
import com.invoicebuilder.tenant.Tenant;
import com.invoicebuilder.tenant.TenantContext;
import com.invoicebuilder.tenant.TenantRepository;
import com.invoicebuilder.user.dto.AcceptInviteRequest;
import com.invoicebuilder.user.dto.ChangeRoleRequest;
import com.invoicebuilder.user.dto.InviteInfoResponse;
import com.invoicebuilder.user.dto.InviteRequest;
import com.invoicebuilder.user.dto.MemberResponse;
import com.invoicebuilder.user.dto.SetActiveRequest;
import com.invoicebuilder.user.dto.TransferOwnershipRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Tenant member management: listing, role changes, (de)activation,
 * ownership transfer, and the email invite flow. Coarse role checks live on
 * the controllers via {@code @PreAuthorize}; the data-dependent rules
 * (self-protection, owner protection) live here.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration INVITE_VALIDITY = Duration.ofDays(7);

    private final AppUserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;
    private final MessageSource messages;
    private final AppProperties appProperties;
    private final Clock clock;

    public UserService(AppUserRepository userRepository,
                       TenantRepository tenantRepository,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService,
                       RefreshTokenService refreshTokenService,
                       MessageSource messages,
                       AppProperties appProperties,
                       Clock clock) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.refreshTokenService = refreshTokenService;
        this.messages = messages;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> list() {
        return userRepository.findByTenantIdOrderByCreatedAtAsc(TenantContext.require())
                .stream().map(MemberResponse::from).toList();
    }

    @Transactional
    public MemberResponse invite(InviteRequest request) {
        UUID tenantId = TenantContext.require();
        String email = request.email().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email already registered");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND, "Tenant not found"));
        AppUser inviter = userRepository.findById(currentUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "User not found"));

        String rawToken = generateToken();
        AppUser user = new AppUser();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setProvider(AuthProvider.LOCAL);
        user.setFullName(email.substring(0, email.indexOf('@')));
        user.setRole(Role.valueOf(request.role()));
        user.setLocale(tenant.getDefaultLocale());
        user.setActive(false);
        user.setInviteTokenHash(sha256(rawToken));
        user.setInvitedAt(OffsetDateTime.now(clock));
        user = userRepository.save(user);

        sendInviteEmail(tenant, inviter, email, rawToken);
        log.info("Invited {} to tenant {} as {}", email, tenantId, request.role());
        return MemberResponse.from(user);
    }

    @Transactional
    public MemberResponse changeRole(UUID id, ChangeRoleRequest request) {
        AppUser target = loadMember(id);
        if (target.getId().equals(currentUserId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "You cannot change your own role");
        }
        if (target.getRole() == Role.OWNER) {
            throw new AppException(ErrorCode.ACCESS_DENIED,
                    "The owner's role can only change via ownership transfer");
        }
        target.setRole(Role.valueOf(request.role()));
        return MemberResponse.from(target);
    }

    @Transactional
    public MemberResponse setActive(UUID id, SetActiveRequest request) {
        AppUser target = loadMember(id);
        if (target.getId().equals(currentUserId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "You cannot deactivate yourself");
        }
        if (target.getRole() == Role.OWNER) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "The owner cannot be deactivated");
        }
        target.setActive(Boolean.TRUE.equals(request.active()));
        if (!target.isActive()) {
            refreshTokenService.revokeAllForUser(target.getId());
        }
        return MemberResponse.from(target);
    }

    @Transactional
    public List<MemberResponse> transferOwnership(TransferOwnershipRequest request) {
        AppUser actor = userRepository.findById(currentUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "User not found"));
        if (actor.getRole() != Role.OWNER) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Only the owner can transfer ownership");
        }
        AppUser target = loadMember(request.targetUserId());
        if (target.getId().equals(actor.getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "You already own this workspace");
        }
        if (!target.isActive()) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Ownership can only go to an active member");
        }
        actor.setRole(Role.ADMIN);
        target.setRole(Role.OWNER);
        log.info("Ownership of tenant {} transferred from {} to {}",
                actor.getTenantId(), actor.getId(), target.getId());
        return List.of(MemberResponse.from(actor), MemberResponse.from(target));
    }

    // ---------- public invite endpoints (anonymous) ----------

    @Transactional(readOnly = true)
    public InviteInfoResponse inviteInfo(String rawToken) {
        AppUser user = loadPendingInvite(rawToken);
        String tenantName = tenantRepository.findById(user.getTenantId())
                .map(Tenant::getName).orElse("");
        return new InviteInfoResponse(user.getEmail(), tenantName);
    }

    @Transactional
    public void acceptInvite(String rawToken, AcceptInviteRequest request) {
        AppUser user = loadPendingInvite(rawToken);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setActive(true);
        user.setInviteTokenHash(null);
        log.info("Invite accepted by {} (tenant={})", user.getEmail(), user.getTenantId());
    }

    // ---------- helpers ----------

    private AppUser loadMember(UUID id) {
        return userRepository.findByIdAndTenantId(id, TenantContext.require())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "User not found"));
    }

    private AppUser loadPendingInvite(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AppException(ErrorCode.INVALID_TOKEN, "Invalid invite");
        }
        AppUser user = userRepository.findByInviteTokenHash(sha256(rawToken))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN, "Invite not found"));
        if (user.isActive() || user.getInvitedAt() == null
                || user.getInvitedAt().plus(INVITE_VALIDITY).isBefore(OffsetDateTime.now(clock))) {
            throw new AppException(ErrorCode.INVALID_TOKEN, "Invite expired");
        }
        return user;
    }

    private void sendInviteEmail(Tenant tenant, AppUser inviter, String email, String rawToken) {
        Locale locale = Locale.forLanguageTag(
                tenant.getDefaultLocale() == null ? "en" : tenant.getDefaultLocale());
        String link = frontendBaseUrl() + "/invite/" + rawToken;
        String subject = messages.getMessage("email.invite.subject",
                new Object[]{tenant.getName()}, locale);
        String body = messages.getMessage("email.invite.body",
                new Object[]{tenant.getName(), inviter.getFullName(), link}, locale);
        emailService.send(new EmailService.EmailMessage(
                email, email, List.of(), List.of(), subject, body, null, null));
    }

    private String frontendBaseUrl() {
        String base = appProperties.oauth2() != null && appProperties.oauth2().successRedirectUri() != null
                ? appProperties.oauth2().successRedirectUri()
                : "http://localhost:5173";
        int idx = base.indexOf("/auth/");
        return idx > 0 ? base.substring(0, idx) : base;
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth == null ? null : auth.getPrincipal();
        if (principal instanceof UserPrincipal up) {
            return up.userId();
        }
        throw new AppException(ErrorCode.AUTHENTICATION_REQUIRED, "Authentication required");
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
