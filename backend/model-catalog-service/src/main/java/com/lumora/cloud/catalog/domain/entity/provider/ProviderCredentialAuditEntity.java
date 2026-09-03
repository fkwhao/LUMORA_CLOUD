package com.lumora.cloud.catalog.domain.entity.provider;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("model_provider_credential_audit")
public class ProviderCredentialAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String credentialId;
    private Long providerId;
    private String action;
    private String actorUserId;
    private String secretFingerprint;

    public ProviderCredentialAuditEntity() {
    }

    public static ProviderCredentialAuditEntity create(
            String credentialId,
            Long providerId,
            String action,
            String actorUserId,
            String secretFingerprint
    ) {
        ProviderCredentialAuditEntity entity = new ProviderCredentialAuditEntity();
        entity.credentialId = credentialId;
        entity.providerId = providerId;
        entity.action = action;
        entity.actorUserId = actorUserId;
        entity.secretFingerprint = secretFingerprint;
        return entity;
    }
}
