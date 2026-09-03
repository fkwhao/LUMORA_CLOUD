package com.lumora.cloud.user.service;

import com.lumora.cloud.user.domain.enums.UserStatus;
import com.lumora.cloud.user.domain.vo.admin.AdminUserPageResponse;
import com.lumora.cloud.user.domain.vo.admin.AdminUserResponse;
import com.lumora.cloud.user.domain.vo.admin.AdminUserStatisticsResponse;
import com.lumora.cloud.user.domain.vo.admin.RoleResponse;
import com.lumora.cloud.user.domain.vo.admin.UserSessionResponse;

import java.util.List;
import java.util.Set;

public interface IUserAdministrationService {

    AdminUserPageResponse search(String query, Long cursor, int limit);

    AdminUserResponse get(Long userId);

    List<RoleResponse> roles();

    List<UserSessionResponse> sessions(Long userId);

    AdminUserResponse updateRoles(Long actorUserId, Long userId, Set<String> requestedRoles);

    AdminUserResponse updateStatus(Long actorUserId, Long userId, UserStatus status);

    UserSessionResponse revokeSession(Long userId, String sessionId);

    AdminUserStatisticsResponse statistics();
}
