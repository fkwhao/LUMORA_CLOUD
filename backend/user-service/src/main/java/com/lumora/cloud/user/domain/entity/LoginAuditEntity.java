package com.lumora.cloud.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumora.cloud.user.domain.enums.ClientType;
import com.lumora.cloud.user.domain.enums.LoginOutcome;

import java.time.Instant;

@TableName("login_audit")
public class LoginAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String sessionId;
    private String email;
    private String clientType;
    private String outcome;
    private String failureReason;
    private String ipAddress;
    private String userAgent;
    private Instant occurredAt;

    public LoginAuditEntity() {
    }

    public static LoginAuditEntity create(
            Long userId,
            String sessionId,
            String email,
            ClientType clientType,
            LoginOutcome outcome,
            String failureReason,
            String ipAddress,
            String userAgent,
            Instant occurredAt
    ) {
        LoginAuditEntity entity = new LoginAuditEntity();
        entity.userId = userId;
        entity.sessionId = sessionId;
        entity.email = email;
        entity.clientType = clientType.name();
        entity.outcome = outcome.name();
        entity.failureReason = failureReason;
        entity.ipAddress = ipAddress;
        entity.userAgent = userAgent;
        entity.occurredAt = occurredAt;
        return entity;
    }
}
