package com.invoicebuilder.auth.oauth2;

import com.invoicebuilder.tenant.Tenant;
import com.invoicebuilder.tenant.TenantRepository;
import com.invoicebuilder.tenant.TenantSlugGenerator;
import com.invoicebuilder.user.AppUser;
import com.invoicebuilder.user.AppUserRepository;
import com.invoicebuilder.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Resolves an {@link AppUser} from an OAuth2 provider's user-info response.
 * Auto-provisions a tenant + owner user on first login per provider identity.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(CustomOAuth2UserService.class);

    private final AppUserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantSlugGenerator slugGenerator;
    private final Clock clock;

    public CustomOAuth2UserService(AppUserRepository userRepository,
                                   TenantRepository tenantRepository,
                                   TenantSlugGenerator slugGenerator,
                                   Clock clock) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.slugGenerator = slugGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User upstream = super.loadUser(request);
        OAuth2UserInfo info = OAuth2UserInfoFactory.getUserInfo(
                request.getClientRegistration().getRegistrationId(),
                upstream.getAttributes());

        if (info.email() == null || info.email().isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_not_provided"),
                    "OAuth2 provider did not return an email address");
        }

        AppUser user = userRepository
                .findByProviderAndProviderId(info.provider(), info.providerId())
                .orElseGet(() -> userRepository.findByEmail(info.email().toLowerCase())
                        .map(existing -> linkProvider(existing, info))
                        .orElseGet(() -> registerNewUser(info)));

        user.setLastLoginAt(OffsetDateTime.now(clock));
        userRepository.save(user);

        return new CustomOAuth2User(user, upstream.getAttributes());
    }

    private AppUser registerNewUser(OAuth2UserInfo info) {
        Tenant tenant = new Tenant();
        String tenantName = info.fullName() != null ? info.fullName() + "'s workspace"
                : info.email().split("@")[0] + "'s workspace";
        tenant.setName(tenantName);
        tenant.setSlug(slugGenerator.generate(tenantName));
        tenant = tenantRepository.save(tenant);

        AppUser user = new AppUser();
        user.setTenantId(tenant.getId());
        user.setEmail(info.email().toLowerCase());
        user.setProvider(info.provider());
        user.setProviderId(info.providerId());
        user.setFullName(info.fullName() != null ? info.fullName() : info.email());
        user.setAvatarUrl(info.avatarUrl());
        user.setRole(Role.OWNER);
        user.setActive(true);
        log.info("Auto-provisioning OAuth2 user {} (provider={}) tenant={}",
                user.getEmail(), info.provider(), tenant.getId());
        return userRepository.save(user);
    }

    private AppUser linkProvider(AppUser existing, OAuth2UserInfo info) {
        if (existing.getProvider() != info.provider()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_already_linked"),
                    "Email is already registered with provider " + existing.getProvider().name().toLowerCase());
        }
        existing.setProviderId(info.providerId());
        if (existing.getAvatarUrl() == null && info.avatarUrl() != null) {
            existing.setAvatarUrl(info.avatarUrl());
        }
        return existing;
    }
}
