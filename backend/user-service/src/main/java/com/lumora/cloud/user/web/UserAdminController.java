package com.lumora.cloud.user.web;

import com.lumora.cloud.user.domain.AuthTypes.UserStatus;
import com.lumora.cloud.user.service.UserAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Validated
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdministrationService userService;

    public UserAdminController(UserAdministrationService userService) {
        this.userService = userService;
    }

    @GetMapping
    public AdminUserPageResponse users(
            @RequestParam(defaultValue = "") @Size(max = 100) String query,
            @RequestParam(required = false) @Min(1) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit
    ) {
        return userService.search(query, cursor, limit);
    }

    @GetMapping("/{userId}")
    public AdminUserResponse user(@PathVariable Long userId) {
        return userService.get(userId);
    }

    @GetMapping("/roles")
    public List<RoleResponse> roles() {
        return userService.roles();
    }

    @GetMapping("/{userId}/sessions")
    public List<UserSessionResponse> sessions(@PathVariable Long userId) {
        return userService.sessions(userId);
    }

    @PutMapping("/{userId}/roles")
    public AdminUserResponse updateRoles(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateRolesRequest request
    ) {
        return userService.updateRoles(actorId(jwt), userId, request.roles());
    }

    @PutMapping("/{userId}/status")
    public AdminUserResponse updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        return userService.updateStatus(actorId(jwt), userId, request.status());
    }

    @PostMapping("/{userId}/sessions/{sessionId}/revoke")
    public UserSessionResponse revokeSession(
            @PathVariable Long userId,
            @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String sessionId
    ) {
        return userService.revokeSession(userId, sessionId);
    }

    @GetMapping("/statistics")
    public AdminUserStatisticsResponse statistics() {
        return userService.statistics();
    }

    private Long actorId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    public record UpdateRolesRequest(
            @NotEmpty Set<@Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}") String> roles
    ) {
        public UpdateRolesRequest {
            roles = roles == null ? Set.of() : Set.copyOf(roles);
        }
    }

    public record UpdateUserStatusRequest(UserStatus status) {
    }

    public record AdminUserResponse(
            Long id,
            String email,
            String displayName,
            String status,
            Set<String> roles,
            long activeSessions,
            Instant createdAt
    ) {
        public AdminUserResponse {
            roles = Set.copyOf(roles);
        }
    }

    public record AdminUserPageResponse(
            List<AdminUserResponse> items,
            Long nextCursor,
            boolean hasMore
    ) {
        public AdminUserPageResponse {
            items = List.copyOf(items);
        }
    }

    public record RoleResponse(String code, String name) {
    }

    public record UserSessionResponse(
            String id,
            String clientType,
            String deviceId,
            String deviceName,
            String ipAddress,
            String userAgent,
            String status,
            Instant expiresAt,
            Instant lastSeenAt,
            Instant revokedAt,
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
