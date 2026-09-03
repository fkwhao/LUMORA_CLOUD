package com.lumora.cloud.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumora.cloud.user.domain.enums.RefreshTokenStatus;

import java.time.Instant;

@TableName("refresh_token")
public class RefreshTokenEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String sessionId;
    private String parentTokenId;
    private String tokenHash;
    private String status;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant revokedAt;
    private Instant createdAt;

    public RefreshTokenEntity() {
    }

    public static RefreshTokenEntity create(
            String id,
            String sessionId,
            String parentTokenId,
            String tokenHash,
            Instant expiresAt
    ) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.id = id;
        entity.sessionId = sessionId;
        entity.parentTokenId = parentTokenId;
        entity.tokenHash = tokenHash;
        entity.status = RefreshTokenStatus.ACTIVE.name();
        entity.expiresAt = expiresAt;
        return entity;
    }

    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
}
