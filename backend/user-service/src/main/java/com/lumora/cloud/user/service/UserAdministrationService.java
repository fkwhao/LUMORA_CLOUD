package com.lumora.cloud.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lumora.cloud.user.persistence.entity.UserAccountEntity;
import com.lumora.cloud.user.persistence.entity.UserSessionEntity;
import com.lumora.cloud.user.persistence.mapper.UserAccountMapper;
import com.lumora.cloud.user.persistence.mapper.UserSessionMapper;
import com.lumora.cloud.user.web.UserAdminController.AdminUserResponse;
import com.lumora.cloud.user.web.UserAdminController.AdminUserStatisticsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class UserAdministrationService {

    private static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Shanghai");

    private final UserAccountMapper userMapper;
    private final UserSessionMapper sessionMapper;

    public UserAdministrationService(UserAccountMapper userMapper, UserSessionMapper sessionMapper) {
        this.userMapper = userMapper;
        this.sessionMapper = sessionMapper;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> search(String query) {
        String keyword = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        var wrapper = Wrappers.<UserAccountEntity>lambdaQuery()
                .orderByDesc(UserAccountEntity::getId)
                .last("LIMIT 50");
        if (!keyword.isBlank()) {
            wrapper.and(condition -> condition
                    .likeRight(UserAccountEntity::getEmail, keyword)
                    .or()
                    .like(UserAccountEntity::getDisplayName, keyword));
        }
        return userMapper.selectList(wrapper).stream()
                .map(user -> new AdminUserResponse(
                        user.getId(), user.getEmail(), user.getDisplayName(), user.getStatus(), user.getCreatedAt()
                ))
                .toList();
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
}
