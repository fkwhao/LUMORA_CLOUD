package com.lumora.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lumora.cloud.user.audit.LoginAuditService;
import com.lumora.cloud.user.cache.SessionCacheService;
import com.lumora.cloud.user.config.AuthProperties;
import com.lumora.cloud.user.domain.enums.ClientType;
import com.lumora.cloud.user.domain.enums.LoginOutcome;
import com.lumora.cloud.user.domain.enums.RefreshTokenStatus;
import com.lumora.cloud.user.domain.enums.SessionStatus;
import com.lumora.cloud.user.domain.enums.UserStatus;
import com.lumora.cloud.user.domain.model.AuthResult;
import com.lumora.cloud.user.domain.model.UserProfile;
import com.lumora.cloud.user.error.ApiException;
import com.lumora.cloud.user.domain.entity.RefreshTokenEntity;
import com.lumora.cloud.user.domain.entity.RoleEntity;
import com.lumora.cloud.user.domain.entity.UserAccountEntity;
import com.lumora.cloud.user.domain.entity.UserRoleEntity;
import com.lumora.cloud.user.domain.entity.UserSessionEntity;
import com.lumora.cloud.user.mapper.RefreshTokenMapper;
import com.lumora.cloud.user.mapper.RoleMapper;
import com.lumora.cloud.user.mapper.UserAccountMapper;
import com.lumora.cloud.user.mapper.UserRoleMapper;
import com.lumora.cloud.user.mapper.UserSessionMapper;
import com.lumora.cloud.user.service.IAuthService;
import com.lumora.cloud.user.utils.JwtTokenService;
import com.lumora.cloud.user.utils.RefreshTokenCodec;
import com.lumora.cloud.user.utils.RequestMetadata;
import com.lumora.cloud.user.domain.dto.auth.LoginRequest;
import com.lumora.cloud.user.domain.dto.auth.RegisterRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthServiceImpl implements IAuthService {

    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserSessionMapper userSessionMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenCodec refreshTokenCodec;
    private final JwtTokenService jwtTokenService;
    private final SessionCacheService sessionCacheService;
    private final LoginAuditService loginAuditService;
    private final AuthProperties properties;
    private final String dummyPasswordHash;

    public AuthServiceImpl(
            UserAccountMapper userAccountMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            UserSessionMapper userSessionMapper,
            RefreshTokenMapper refreshTokenMapper,
            PasswordEncoder passwordEncoder,
            RefreshTokenCodec refreshTokenCodec,
            JwtTokenService jwtTokenService,
            SessionCacheService sessionCacheService,
            LoginAuditService loginAuditService,
            AuthProperties properties
    ) {
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.userSessionMapper = userSessionMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenCodec = refreshTokenCodec;
        this.jwtTokenService = jwtTokenService;
        this.sessionCacheService = sessionCacheService;
        this.loginAuditService = loginAuditService;
        this.properties = properties;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    @Override
    public AuthResult register(RegisterRequest request, RequestMetadata metadata) {
        String email = normalizeEmail(request.email());
        Long existing = userAccountMapper.selectCount(Wrappers.<UserAccountEntity>lambdaQuery()
                .eq(UserAccountEntity::getEmail, email));
        if (existing > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "该邮箱已经注册");
        }

        UserAccountEntity account = UserAccountEntity.create(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim()
        );
        try {
            userAccountMapper.insert(account);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "该邮箱已经注册");
        }

        RoleEntity userRole = roleMapper.findByCode("USER");
        if (userRole == null) {
            throw new IllegalStateException("Default USER role is missing");
        }
        userRoleMapper.insert(UserRoleEntity.create(account.getId(), userRole.getId()));
        return issueSession(account, Set.of("USER"), request.clientType(), request.deviceId(), request.deviceName(), metadata);
    }

    @Transactional
    @Override
    public AuthResult login(LoginRequest request, RequestMetadata metadata) {
        String email = normalizeEmail(request.email());
        UserAccountEntity account = findByEmail(email);
        if (account == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            loginAuditService.record(null, null, email, request.clientType(), LoginOutcome.FAILURE,
                    "INVALID_CREDENTIALS", metadata, Instant.now());
            throw invalidCredentials();
        }

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            loginAuditService.record(account.getId(), null, email, request.clientType(), LoginOutcome.FAILURE,
                    "INVALID_CREDENTIALS", metadata, Instant.now());
            throw invalidCredentials();
        }
        ensureAccountActive(account);

        Set<String> roles = rolesFor(account.getId());
        AuthResult result = issueSession(account, roles, request.clientType(), request.deviceId(), request.deviceName(), metadata);
        loginAuditService.record(account.getId(), result.sessionId(), email, request.clientType(), LoginOutcome.SUCCESS,
                null, metadata, Instant.now());
        return result;
    }

    @Transactional(noRollbackFor = ApiException.class)
    @Override
    public AuthResult refresh(String rawRefreshToken, RequestMetadata metadata) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw invalidRefreshToken();
        }

        Instant now = Instant.now();
        RefreshTokenEntity currentToken = refreshTokenMapper.findByHashForUpdate(refreshTokenCodec.hash(rawRefreshToken));
        if (currentToken == null) {
            throw invalidRefreshToken();
        }

        UserSessionEntity session = userSessionMapper.findByIdForUpdate(currentToken.getSessionId());
        if (session == null) {
            throw invalidRefreshToken();
        }

        if (!RefreshTokenStatus.ACTIVE.name().equals(currentToken.getStatus())) {
            revokeSession(session, now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED", "登录凭据已被重复使用，请重新登录");
        }
        if (!SessionStatus.ACTIVE.name().equals(session.getStatus())
                || !currentToken.getExpiresAt().isAfter(now)
                || !session.getExpiresAt().isAfter(now)) {
            revokeSession(session, now);
            throw invalidRefreshToken();
        }

        UserAccountEntity account = userAccountMapper.selectById(session.getUserId());
        if (account == null || !UserStatus.ACTIVE.name().equals(account.getStatus())) {
            revokeSession(session, now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_UNAVAILABLE", "账号当前不可用");
        }

        if (refreshTokenMapper.markUsed(currentToken.getId(), now) != 1) {
            revokeSession(session, now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED", "登录凭据已被重复使用，请重新登录");
        }

        String newRawRefreshToken = refreshTokenCodec.generate();
        RefreshTokenEntity newToken = RefreshTokenEntity.create(
                UUID.randomUUID().toString(),
                session.getId(),
                currentToken.getId(),
                refreshTokenCodec.hash(newRawRefreshToken),
                session.getExpiresAt()
        );
        refreshTokenMapper.insert(newToken);
        userSessionMapper.touch(session.getId(), now, metadata.ipAddress(), metadata.userAgent());
        sessionCacheService.remember(session.getId(), session.getUserId(), session.getExpiresAt(), now);

        Set<String> roles = rolesFor(account.getId());
        ClientType clientType = ClientType.valueOf(session.getClientType());
        JwtTokenService.AccessToken accessToken = jwtTokenService.issue(
                account.getId(), session.getId(), session.getDeviceId(), clientType, roles,
                account.getTokenVersion(), now
        );
        return new AuthResult(
                session.getId(),
                clientType,
                accessToken,
                newRawRefreshToken,
                session.getExpiresAt(),
                profile(account, roles)
        );
    }

    @Transactional
    @Override
    public void logout(String rawRefreshToken, String authenticatedSessionId) {
        String sessionId = authenticatedSessionId;
        if (StringUtils.hasText(rawRefreshToken)) {
            RefreshTokenEntity token = refreshTokenMapper.findByHashForUpdate(refreshTokenCodec.hash(rawRefreshToken));
            if (token != null) {
                sessionId = token.getSessionId();
            }
        }
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        UserSessionEntity session = userSessionMapper.findByIdForUpdate(sessionId);
        if (session != null) {
            revokeSession(session, Instant.now());
        }
    }

    @Transactional(readOnly = true)
    @Override
    public UserProfile profile(Long userId) {
        UserAccountEntity account = userAccountMapper.selectById(userId);
        if (account == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        ensureAccountActive(account);
        return profile(account, rolesFor(userId));
    }

    private AuthResult issueSession(
            UserAccountEntity account,
            Set<String> roles,
            ClientType clientType,
            String requestedDeviceId,
            String requestedDeviceName,
            RequestMetadata metadata
    ) {
        Instant now = Instant.now();
        Instant sessionExpiresAt = now.plus(properties.refreshTokenTtl());
        String sessionId = UUID.randomUUID().toString();
        String deviceId = StringUtils.hasText(requestedDeviceId) ? requestedDeviceId.trim() : UUID.randomUUID().toString();
        String deviceName = StringUtils.hasText(requestedDeviceName) ? requestedDeviceName.trim() : clientType.name();

        userSessionMapper.insert(UserSessionEntity.create(
                sessionId,
                account.getId(),
                clientType,
                deviceId,
                deviceName,
                metadata.ipAddress(),
                metadata.userAgent(),
                now,
                sessionExpiresAt
        ));

        String rawRefreshToken = refreshTokenCodec.generate();
        refreshTokenMapper.insert(RefreshTokenEntity.create(
                UUID.randomUUID().toString(),
                sessionId,
                null,
                refreshTokenCodec.hash(rawRefreshToken),
                sessionExpiresAt
        ));
        sessionCacheService.remember(sessionId, account.getId(), sessionExpiresAt, now);

        JwtTokenService.AccessToken accessToken = jwtTokenService.issue(
                account.getId(), sessionId, deviceId, clientType, roles, account.getTokenVersion(), now
        );
        return new AuthResult(sessionId, clientType, accessToken, rawRefreshToken, sessionExpiresAt, profile(account, roles));
    }

    private void revokeSession(UserSessionEntity session, Instant now) {
        userSessionMapper.revoke(session.getId(), now);
        refreshTokenMapper.revokeActiveBySessionId(session.getId(), now);
        sessionCacheService.revoke(session.getId(), session.getExpiresAt(), now);
    }

    private UserAccountEntity findByEmail(String email) {
        return userAccountMapper.selectOne(Wrappers.<UserAccountEntity>lambdaQuery()
                .eq(UserAccountEntity::getEmail, email)
                .last("LIMIT 1"));
    }

    private Set<String> rolesFor(Long userId) {
        Set<String> roles = userRoleMapper.findRoleCodesByUserId(userId);
        if (roles == null || roles.isEmpty()) {
            throw new IllegalStateException("User has no assigned role: " + userId);
        }
        return Set.copyOf(roles);
    }

    private UserProfile profile(UserAccountEntity account, Set<String> roles) {
        return new UserProfile(account.getId().toString(), account.getEmail(), account.getDisplayName(), account.getStatus(), roles);
    }

    private void ensureAccountActive(UserAccountEntity account) {
        if (!UserStatus.ACTIVE.name().equals(account.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "账号已被停用");
        }
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "邮箱或密码不正确");
    }

    private ApiException invalidRefreshToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "登录状态已失效，请重新登录");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

}
