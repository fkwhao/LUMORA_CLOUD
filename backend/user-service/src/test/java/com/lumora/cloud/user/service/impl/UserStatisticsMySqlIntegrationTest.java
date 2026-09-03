package com.lumora.cloud.user.service.impl;

import com.lumora.cloud.user.service.IUserAdministrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false"
        }
)
@EnabledIfEnvironmentVariable(named = "LUMORA_RUN_USER_TESTS", matches = "true")
class UserStatisticsMySqlIntegrationTest {

    @Autowired
    private IUserAdministrationService userService;

    @Test
    void readsExactStatisticsAgainstMySql() {
        var statistics = userService.statistics();

        assertThat(statistics.totalUsers()).isGreaterThanOrEqualTo(0);
        assertThat(statistics.activeUsers() + statistics.disabledUsers())
                .isEqualTo(statistics.totalUsers());
        assertThat(statistics.createdThisMonth()).isBetween(0L, statistics.totalUsers());
        assertThat(statistics.activeSessions()).isGreaterThanOrEqualTo(0);
        assertThat(statistics.reportingZone()).isEqualTo("Asia/Shanghai");
        assertThat(statistics.generatedAt()).isNotNull();
    }
}
