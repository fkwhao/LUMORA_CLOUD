package com.lumora.cloud.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lumora.cloud.user.domain.AuthTypes.UserStatus;
import com.lumora.cloud.user.error.ApiException;
import com.lumora.cloud.user.persistence.entity.RoleEntity;
import com.lumora.cloud.user.persistence.entity.UserAccountEntity;
import com.lumora.cloud.user.persistence.entity.UserRoleEntity;
import com.lumora.cloud.user.persistence.entity.UserSessionEntity;
import com.lumora.cloud.user.persistence.mapper.RefreshTokenMapper;
import com.lumora.cloud.user.persistence.mapper.RoleMapper;
import com.lumora.cloud.user.persistence.mapper.UserAccountMapper;
import com.lumora.cloud.user.persistence.mapper.UserRoleMapper;
import com.lumora.cloud.user.persistence.mapper.UserSessionMapper;
import com.lumora.cloud.user.persistence.projection.ActiveSessionCountView;
import com.lumora.cloud.user.persistence.projection.UserRoleCodeView;
import com.lumora.cloud.user.service.SessionsRevokedEvent.RevokedSession;
import com.lumora.cloud.user.web.UserAdminController.AdminUserPageResponse;
import com.lumora.cloud.user.web.UserAdminController.AdminUserResponse;
import com.lumora.cloud.user.web.UserAdminController.AdminUserStatisticsResponse;
import com.lumora.cloud.user.web.UserAdminController.RoleResponse;
import com.lumora.cloud.user.web.UserAdminController.UserSessionResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserAdministrationService {

    private static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String USER_ROLE = "USER";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final int MIN_SEARCH_LENGTH = 2;
    private static final int MAX_PAGE_SIZE = 50;

    private final UserAccountMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserSessionMapper sessionMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final ApplicationEventPublisher events;

    public UserAdministrationService(
            UserAccountMapper userMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            UserSessionMapper sessionMapper,
            RefreshTokenMapper refreshTokenMapper,
            ApplicationEventPublisher events
    ) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.sessionMapper = sessionMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public AdminUserPageResponse search(String query, Long cursor, int limit) {
        String keyword = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (!keyword.isBlank() && keyword.length() < MIN_SEARCH_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_SEARCH_QUERY_TOO_SHORT", "搜索关键字至少需要 2 个字符");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_SEARCH_LIMIT_INVALID", "每页数量必须在 1 到 50 之间");
        }

        var wrapper = Wrappers.<UserAccountEntity>lambdaQuery()
                .lt(cursor != null, UserAccountEntity::getId, cursor)
                .orderByDesc(UserAccountEntity::getId)
                .last("LIMIT " + (limit + 1));
        if (!keyword.isBlank()) {
            wrapper.and(condition -> condition
                    .likeRight(UserAccountEntity::getEmail, keyword)
                    .or()
                    .likeRight(UserAccountEntity::getDisplayName, keyword));
        }

        List<UserAccountEntity> fetched = userMapper.selectList(wrapper);
        boolean hasMore = fetched.size() > limit;
        List<UserAccountEntity> page = hasMore ? fetched.subList(0, limit) : fetched;
        List<AdminUserResponse> items = responses(page);
        Long nextCursor = hasMore && !page.isEmpty() ? page.get(page.size() - 1).getId() : null;
        return new AdminUserPageResponse(items, nextCursor, hasMore);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(Long userId) {
        return response(requireUser(userId));
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> roles() {
        return roleMapper.selectList(Wrappers.<RoleEntity>lambdaQuery().orderByAsc(RoleEntity::getCode))
                .stream()
                .map(role -> new RoleResponse(role.getCode(), role.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserSessionResponse> sessions(Long userId) {
        requireUser(userId);
        return sessionMapper.findRecentByUserId(userId).stream().map(this::sessionResponse).toList();
    }

    @Transactional
    public AdminUserResponse updateRoles(Long actorUserId, Long userId, Set<String> requestedRoles) {
        UserAccountEntity user = requireUserForUpdate(userId);
        Set<String> normalized = requestedRoles.stream()
                .map(value -> value == null ? "" : value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!normalized.contains(USER_ROLE)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_ROLE_REQUIRED", "所有账号都必须保留 USER 角色");
        }

        Map<String, RoleEntity> available = roleMapper.selectList(Wrappers.<RoleEntity>lambdaQuery())
                .stream()
                .collect(Collectors.toMap(RoleEntity::getCode, Function.identity()));
        if (!available.keySet().containsAll(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_ROLE", "请求中包含不存在的角色");
        }

        Set<String> current = userRoleMapper.findRoleCodesByUserId(userId);
        if (current.equals(normalized)) {
            return response(user);
        }
        if (actorUserId.equals(userId) && current.contains(ADMIN_ROLE) && !normalized.contains(ADMIN_ROLE)) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_REMOVE_OWN_ADMIN", "不能移除自己的管理员角色");
        }
        if (current.contains(ADMIN_ROLE) && !normalized.contains(ADMIN_ROLE)
                && UserStatus.ACTIVE.name().equals(user.getStatus())) {
            ensureAnotherActiveAdmin();
        }

        userRoleMapper.deleteByUserId(userId);
        normalized.forEach(code -> userRoleMapper.insert(UserRoleEntity.create(userId, available.get(code).getId())));
        userMapper.incrementTokenVersion(userId);
        revokeAllSessions(userId, Instant.now());
        return response(userMapper.selectById(userId));
    }

    @Transactional
    public AdminUserResponse updateStatus(Long actorUserId, Long userId, UserStatus status) {
        if (status == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_STATUS_REQUIRED", "必须指定用户状态");
        }
        UserAccountEntity user = requireUserForUpdate(userId);
        if (status.name().equals(user.getStatus())) {
            return response(user);
        }
        if (actorUserId.equals(userId) && status == UserStatus.DISABLED) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_DISABLE_SELF", "不能停用当前登录的管理员账号");
        }
        if (status == UserStatus.DISABLED
                && userRoleMapper.findRoleCodesByUserId(userId).contains(ADMIN_ROLE)) {
            ensureAnotherActiveAdmin();
        }

        userMapper.updateStatusAndTokenVersion(userId, status.name());
        if (status == UserStatus.DISABLED) {
            revokeAllSessions(userId, Instant.now());
        }
        return response(userMapper.selectById(userId));
    }

    @Transactional
    public UserSessionResponse revokeSession(Long userId, String sessionId) {
        requireUserForUpdate(userId);
        UserSessionEntity session = sessionMapper.findByIdForUpdate(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "登录会话不存在");
        }
        if (!"ACTIVE".equals(session.getStatus())) {
            return sessionResponse(session);
        }
        Instant now = Instant.now();
        refreshTokenMapper.revokeActiveBySessionId(sessionId, now);
        sessionMapper.revoke(sessionId, now);
        events.publishEvent(new SessionsRevokedEvent(
                List.of(new RevokedSession(sessionId, session.getExpiresAt())), now
        ));
        return sessionResponse(sessionMapper.selectById(sessionId));
    }

    @Transactional(readOnly = true)
    public AdminUserStatisticsResponse statistics() {
        Instant now = Instant.now();
        ZonedDateTime localNow = now.atZone(REPORTING_ZONE);
        Instant monthStart = localNow.toLocalDate().withDayOfMonth(1)
                .atStartOfDay(REPORTING_ZONE).toInstant();
        Instant nextMonthStart = localNow.toLocalDate().withDayOfMonth(1).plusMonths(1)
                .atStartOfDay(REPORTING_ZONE).toInstant();

        long totalUsers = userMapper.selectCount(Wrappers.<UserAccountEntity>lambdaQuery());
        long activeUsers = userMapper.selectCount(Wrappers.<UserAccountEntity>lambdaQuery()
                .eq(UserAccountEntity::getStatus, "ACTIVE"));
        long disabledUsers = userMapper.selectCount(Wrappers.<UserAccountEntity>lambdaQuery()
                .eq(UserAccountEntity::getStatus, "DISABLED"));
        long createdThisMonth = userMapper.selectCount(Wrappers.<UserAccountEntity>lambdaQuery()
                .ge(UserAccountEntity::getCreatedAt, monthStart)
                .lt(UserAccountEntity::getCreatedAt, nextMonthStart));
        long activeSessions = sessionMapper.selectCount(Wrappers.<UserSessionEntity>lambdaQuery()
                .eq(UserSessionEntity::getStatus, "ACTIVE")
                .gt(UserSessionEntity::getExpiresAt, now));

        return new AdminUserStatisticsResponse(
                totalUsers, activeUsers, disabledUsers, createdThisMonth, activeSessions,
                REPORTING_ZONE.getId(), now
        );
    }

    private AdminUserResponse response(UserAccountEntity user) {
        Instant now = Instant.now();
        long activeSessions = sessionMapper.selectCount(Wrappers.<UserSessionEntity>lambdaQuery()
                .eq(UserSessionEntity::getUserId, user.getId())
                .eq(UserSessionEntity::getStatus, "ACTIVE")
                .gt(UserSessionEntity::getExpiresAt, now));
        return new AdminUserResponse(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getStatus(),
                userRoleMapper.findRoleCodesByUserId(user.getId()), activeSessions, user.getCreatedAt()
        );
    }

    private List<AdminUserResponse> responses(List<UserAccountEntity> users) {
        if (users.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = users.stream().map(UserAccountEntity::getId).toList();
        Map<Long, Set<String>> rolesByUser = new LinkedHashMap<>();
        for (UserRoleCodeView row : userRoleMapper.findRoleCodesByUserIds(userIds)) {
            rolesByUser.computeIfAbsent(row.getUserId(), ignored -> new LinkedHashSet<>()).add(row.getRoleCode());
        }
        Map<Long, Long> activeSessionsByUser = sessionMapper.countActiveByUserIds(userIds, Instant.now()).stream()
                .collect(Collectors.toMap(ActiveSessionCountView::getUserId, ActiveSessionCountView::getActiveSessions));

        return users.stream()
                .map(user -> new AdminUserResponse(
                        user.getId(), user.getEmail(), user.getDisplayName(), user.getStatus(),
                        Collections.unmodifiableSet(rolesByUser.getOrDefault(user.getId(), Set.of())),
                        activeSessionsByUser.getOrDefault(user.getId(), 0L), user.getCreatedAt()
                ))
                .toList();
    }

    private UserSessionResponse sessionResponse(UserSessionEntity session) {
        return new UserSessionResponse(
                session.getId(), session.getClientType(), session.getDeviceId(), session.getDeviceName(),
                session.getIpAddress(), session.getUserAgent(), session.getStatus(), session.getExpiresAt(),
                session.getLastSeenAt(), session.getRevokedAt(), session.getCreatedAt()
        );
    }

    private UserAccountEntity requireUser(Long userId) {
        UserAccountEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        return user;
    }

    private UserAccountEntity requireUserForUpdate(Long userId) {
        UserAccountEntity user = userMapper.findByIdForUpdate(userId);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        return user;
    }

    private void ensureAnotherActiveAdmin() {
        roleMapper.findByCodeForUpdate(ADMIN_ROLE);
        if (userRoleMapper.countActiveUsersByRole(ADMIN_ROLE) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ACTIVE_ADMIN", "平台必须至少保留一个启用中的管理员");
        }
    }

    private void revokeAllSessions(Long userId, Instant now) {
        List<UserSessionEntity> active = sessionMapper.findActiveByUserIdForUpdate(userId);
        if (active.isEmpty()) {
            return;
        }
        refreshTokenMapper.revokeActiveByUserId(userId, now);
        sessionMapper.revokeActiveByUserId(userId, now);
        events.publishEvent(new SessionsRevokedEvent(
                active.stream().map(session -> new RevokedSession(session.getId(), session.getExpiresAt())).toList(),
                now
        ));
    }
}
