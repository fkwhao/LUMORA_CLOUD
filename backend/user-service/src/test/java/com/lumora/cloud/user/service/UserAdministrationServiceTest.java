package com.lumora.cloud.user.service;

import com.lumora.cloud.user.persistence.mapper.UserAccountMapper;
import com.lumora.cloud.user.persistence.mapper.UserSessionMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAdministrationServiceTest {

    @Test
    void returnsExactDomainCountsWithoutClientSideSampling() {
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        UserSessionMapper sessionMapper = mock(UserSessionMapper.class);
        when(userMapper.selectCount(any())).thenReturn(12L, 9L, 3L, 4L);
        when(sessionMapper.selectCount(any())).thenReturn(5L);

        var statistics = new UserAdministrationService(userMapper, sessionMapper).statistics();

        assertThat(statistics.totalUsers()).isEqualTo(12);
        assertThat(statistics.activeUsers()).isEqualTo(9);
        assertThat(statistics.disabledUsers()).isEqualTo(3);
        assertThat(statistics.createdThisMonth()).isEqualTo(4);
        assertThat(statistics.activeSessions()).isEqualTo(5);
        assertThat(statistics.reportingZone()).isEqualTo("Asia/Shanghai");
        assertThat(statistics.generatedAt()).isNotNull();
    }
}
