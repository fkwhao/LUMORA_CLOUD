package com.lumora.cloud.user.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumora.cloud.user.domain.AuthTypes.ClientType;
import com.lumora.cloud.user.domain.AuthTypes.SessionStatus;

import java.time.Instant;

@TableName("user_session")
public class UserSessionEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private Long userId;
    private String clientType;
    private String deviceId;
    private String deviceName;
    private String ipAddress;
    private String userAgent;
    private String status;
    private Instant expiresAt;
    private Instant lastSeenAt;
    private Instant revokedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public UserSessionEntity() {
    }

    public static UserSessionEntity create(
            String id,
            Long userId,
            ClientType clientType,
            String deviceId,
            String deviceName,
            String ipAddress,
            String userAgent,
            Instant now,
            Instant expiresAt
    ) {
        UserSessionEntity entity = new UserSessionEntity();
        entity.id = id;
        entity.userId = userId;
        entity.clientType = clientType.name();
        entity.deviceId = deviceId;
        entity.deviceName = deviceName;
        entity.ipAddress = ipAddress;
        entity.userAgent = userAgent;
        entity.status = SessionStatus.ACTIVE.name();
        entity.expiresAt = expiresAt;
        entity.lastSeenAt = now;
        return entity;
    }

    public String getId() { return id; }
    public Long getUserId() { return userId; }
    public String getClientType() { return clientType; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public String getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
