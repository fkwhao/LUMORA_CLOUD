package com.lumora.cloud.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumora.cloud.user.domain.enums.UserStatus;

import java.time.Instant;

@TableName("user_account")
public class UserAccountEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String email;
    private String passwordHash;
    private String displayName;
    private String status;
    private Integer tokenVersion;
    @TableLogic
    private Integer deleted;
    private Instant createdAt;
    private Instant updatedAt;

    public UserAccountEntity() {
    }

    public static UserAccountEntity create(String email, String passwordHash, String displayName) {
        UserAccountEntity entity = new UserAccountEntity();
        entity.email = email;
        entity.passwordHash = passwordHash;
        entity.displayName = displayName;
        entity.status = UserStatus.ACTIVE.name();
        entity.tokenVersion = 0;
        entity.deleted = 0;
        return entity;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getStatus() { return status; }
    public Integer getTokenVersion() { return tokenVersion; }
    public Instant getCreatedAt() { return createdAt; }
}
