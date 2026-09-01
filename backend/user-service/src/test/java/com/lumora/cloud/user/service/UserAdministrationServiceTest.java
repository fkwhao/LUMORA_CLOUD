package com.lumora.cloud.user.service;

import com.lumora.cloud.user.error.ApiException;
import com.lumora.cloud.user.persistence.entity.UserAccountEntity;
import com.lumora.cloud.user.persistence.projection.ActiveSessionCountView;
import com.lumora.cloud.user.persistence.projection.UserRoleCodeView;
import com.lumora.cloud.user.persistence.mapper.UserAccountMapper;
import com.lumora.cloud.user.persistence.mapper.RefreshTokenMapper;
import com.lumora.cloud.user.persistence.mapper.RoleMapper;
import com.lumora.cloud.user.persistence.mapper.UserRoleMapper;
import com.lumora.cloud.user.persistence.mapper.UserSessionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAdministrationServiceTest {

    @Test
    void returnsExactDomainCountsWithoutClientSideSampling() {
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        UserSessionMapper sessionMapper = mock(UserSessionMapper.class);
        when(userMapper.selectCount(any())).thenReturn(12L, 9L, 3L, 4L);
        when(sessionMapper.selectCount(any())).thenReturn(5L);

        var statistics = new UserAdministrationService(
                userMapper,
                mock(RoleMapper.class),
                mock(UserRoleMapper.class),
                sessionMapper,
                mock(RefreshTokenMapper.class),
                mock(ApplicationEventPublisher.class)
        ).statistics();

        assertThat(statistics.totalUsers()).isEqualTo(12);
        assertThat(statistics.activeUsers()).isEqualTo(9);
        assertThat(statistics.disabledUsers()).isEqualTo(3);
        assertThat(statistics.createdThisMonth()).isEqualTo(4);
        assertThat(statistics.activeSessions()).isEqualTo(5);
        assertThat(statistics.reportingZone()).isEqualTo("Asia/Shanghai");
        assertThat(statistics.generatedAt()).isNotNull();
    }

    @Test
    void returnsCursorPageWithRolesAndSessionsLoadedInBatches() {
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        UserSessionMapper sessionMapper = mock(UserSessionMapper.class);
        when(userMapper.selectList(any())).thenReturn(List.of(
                user(30L, "first@lumora.com", "First"),
                user(20L, "second@lumora.com", "Second"),
                user(10L, "third@lumora.com", "Third")
        ));
        when(userRoleMapper.findRoleCodesByUserIds(List.of(30L, 20L))).thenReturn(List.of(
                role(30L, "ADMIN"), role(30L, "USER"), role(20L, "USER")
        ));
        when(sessionMapper.countActiveByUserIds(eq(List.of(30L, 20L)), any(Instant.class))).thenReturn(List.of(
                sessions(30L, 2L)
        ));

        var page = service(userMapper, userRoleMapper, sessionMapper).search("", null, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isEqualTo(20L);
        assertThat(page.items().get(0).roles()).containsExactlyInAnyOrder("ADMIN", "USER");
        assertThat(page.items().get(0).activeSessions()).isEqualTo(2L);
        assertThat(page.items().get(1).activeSessions()).isZero();
        verify(userRoleMapper).findRoleCodesByUserIds(List.of(30L, 20L));
        verify(sessionMapper).countActiveByUserIds(eq(List.of(30L, 20L)), any(Instant.class));
    }

    @Test
    void rejectsOneCharacterSearchTerms() {
        var service = service(
                mock(UserAccountMapper.class), mock(UserRoleMapper.class), mock(UserSessionMapper.class)
        );

        assertThatThrownBy(() -> service.search("a", null, 20))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("USER_SEARCH_QUERY_TOO_SHORT");
                    assertThat(exception.getMessage()).contains("至少需要 2 个字符");
                });
    }

    private UserAdministrationService service(
            UserAccountMapper userMapper,
            UserRoleMapper userRoleMapper,
            UserSessionMapper sessionMapper
    ) {
        return new UserAdministrationService(
                userMapper,
                mock(RoleMapper.class),
                userRoleMapper,
                sessionMapper,
                mock(RefreshTokenMapper.class),
                mock(ApplicationEventPublisher.class)
        );
    }

    private UserAccountEntity user(Long id, String email, String displayName) {
        UserAccountEntity user = UserAccountEntity.create(email, "hash", displayName);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-09-01T00:00:00Z"));
        return user;
    }

    private UserRoleCodeView role(Long userId, String code) {
        UserRoleCodeView row = new UserRoleCodeView();
        row.setUserId(userId);
        row.setRoleCode(code);
        return row;
    }

    private ActiveSessionCountView sessions(Long userId, Long count) {
        ActiveSessionCountView row = new ActiveSessionCountView();
        row.setUserId(userId);
        row.setActiveSessions(count);
        return row;
    }
}
