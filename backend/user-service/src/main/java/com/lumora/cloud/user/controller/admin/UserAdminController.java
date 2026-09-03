package com.lumora.cloud.user.controller.admin;

import com.lumora.cloud.user.domain.dto.admin.UpdateRolesRequest;
import com.lumora.cloud.user.domain.dto.admin.UpdateUserStatusRequest;
import com.lumora.cloud.user.domain.vo.admin.AdminUserPageResponse;
import com.lumora.cloud.user.domain.vo.admin.AdminUserResponse;
import com.lumora.cloud.user.domain.vo.admin.AdminUserStatisticsResponse;
import com.lumora.cloud.user.domain.vo.admin.RoleResponse;
import com.lumora.cloud.user.domain.vo.admin.UserSessionResponse;
import com.lumora.cloud.user.service.IUserAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final IUserAdministrationService userService;

    public UserAdminController(IUserAdministrationService userService) {
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

}
