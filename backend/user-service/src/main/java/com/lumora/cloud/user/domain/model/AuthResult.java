package com.lumora.cloud.user.domain.model;

import com.lumora.cloud.user.domain.enums.ClientType;
import com.lumora.cloud.user.utils.JwtTokenService;

import java.time.Instant;

public record AuthResult(
        String sessionId,
        ClientType clientType,
        JwtTokenService.AccessToken accessToken,
        String refreshToken,
        Instant sessionExpiresAt,
        UserProfile user
) {
}
