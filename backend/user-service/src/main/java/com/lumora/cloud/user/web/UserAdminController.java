package com.lumora.cloud.user.web;

import com.lumora.cloud.user.service.UserAdministrationService;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdministrationService userService;

    public UserAdminController(UserAdministrationService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<AdminUserResponse> users(
            @RequestParam(defaultValue = "") @Size(max = 100) String query
    ) {
        return userService.search(query);
    }

    @GetMapping("/statistics")
    public AdminUserStatisticsResponse statistics() {
        return userService.statistics();
    }

    public record AdminUserResponse(
            Long id,
            String email,
            String displayName,
            String status,
            Instant createdAt
    ) {
    }

    public record AdminUserStatisticsResponse(
            long totalUsers,
            long activeUsers,
            long disabledUsers,
            long createdThisMonth,
            long activeSessions,
            String reportingZone,
            Instant generatedAt
    ) {
    }
}
