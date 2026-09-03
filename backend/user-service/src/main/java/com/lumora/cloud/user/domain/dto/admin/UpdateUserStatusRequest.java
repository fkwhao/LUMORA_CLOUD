package com.lumora.cloud.user.domain.dto.admin;

import com.lumora.cloud.user.domain.enums.UserStatus;

public record UpdateUserStatusRequest(UserStatus status) {
}
