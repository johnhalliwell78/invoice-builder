package com.invoicebuilder.auth;

import com.invoicebuilder.auth.dto.AuthResponse;
import com.invoicebuilder.auth.dto.LoginRequest;
import com.invoicebuilder.auth.dto.RegisterRequest;
import com.invoicebuilder.auth.dto.UserResponse;
import com.invoicebuilder.auth.jwt.JwtService;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.config.AppProperties;
import com.invoicebuilder.tenant.Tenant;
import com.invoicebuilder.tenant.TenantRepository;
import com.invoicebuilder.tenant.TenantSlugGenerator;
import com.invoicebuilder.user.AppUser;
import com.invoicebuilder.user.AppUserRepository;
import com.invoicebuilder.user.AuthProvider;
import com.invoicebuilder.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final TenantRepository tenantRepository;
    private final AppUserRepository userRepository;
    private final TenantSlugGenerator slugGenerator;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AppProperties appProperties;
    private final Clock clock;

    public AuthService(TenantRepository tenantRepository,
                       AppUserRepository userRepository,
                       TenantSlugGenerator slugGenerator,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       AppProperties appProperties,
                       Clock clock) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.slugGenerator = slugGenerator;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    public record AuthResult(AuthResponse authResponse, String refreshToken) {
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        String email = request.email().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email already registered");
        }

        Tenant tenant = new Tenant();
        tenant.setName(request.tenantName().trim());
        tenant.setSlug(slugGenerator.generate(request.tenantName()));
        if (request.defaultCurrency() != null) {
            tenant.setDefaultCurrency(request.defaultCurrency().toUpperCase());
        }
        if (request.defaultLocale() != null) {
            tenant.setDefaultLocale(request.defaultLocale());
        }
        tenant = tenantRepository.save(tenant);

        AppUser user = new AppUser();
        user.setTenantId(tenant.getId());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setProvider(AuthProvider.LOCAL);
        user.setFullName(request.fullName().trim());
        user.setRole(Role.OWNER);
        user.setLocale(tenant.getDefaultLocale());
        user.setActive(true);
        user = userRepository.save(user);

        log.info("Registered new tenant {} (slug={}) with owner user {}", tenant.getId(), tenant.getSlug(), user.getId());
        return issueTokens(user);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        String email = request.email().toLowerCase();
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException e) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        AppUser user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "User not found"));

        if (!user.isActive()) {
            throw new AppException(ErrorCode.USER_INACTIVE, "User account is deactivated");
        }

        user.setLastLoginAt(OffsetDateTime.now(clock));
        userRepository.save(user);

        log.info("Login successful for user {} (tenant={})", user.getId(), user.getTenantId());
        return issueTokens(user);
    }

    @Transactional
    public AuthResult refresh(String refreshToken) {
        UUID userId = refreshTokenService.resolveUserId(refreshToken);
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_INVALID, "User not found"));
        if (!user.isActive()) {
            throw new AppException(ErrorCode.USER_INACTIVE, "User account is deactivated");
        }

        RefreshTokenService.IssuedToken rotated = refreshTokenService.rotate(refreshToken);
        String accessToken = jwtService.generateAccessToken(user);
        AuthResponse body = AuthResponse.of(
                accessToken,
                appProperties.jwt().accessTokenExpiry().toSeconds(),
                UserResponse.from(user));
        return new AuthResult(body, rotated.rawToken());
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenService.revoke(refreshToken);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "User not found"));
    }

    private AuthResult issueTokens(AppUser user) {
        String accessToken = jwtService.generateAccessToken(user);
        RefreshTokenService.IssuedToken refresh = refreshTokenService.issue(user.getId());
        AuthResponse response = AuthResponse.of(
                accessToken,
                appProperties.jwt().accessTokenExpiry().toSeconds(),
                UserResponse.from(user));
        return new AuthResult(response, refresh.rawToken());
    }
}
