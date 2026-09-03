package com.lumora.cloud.catalog.domain.entity.provider;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("model_provider_credential")
public class ProviderCredentialEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private Long providerId;
    private String credentialReference;
    private byte[] encryptedSecret;
    private byte[] encryptionNonce;
    private Integer encryptionKeyVersion;
    private String secretFingerprint;
    private String secretHint;
    private String status;
    private Long revision;
    private Instant createdAt;
    private Instant rotatedAt;

    public ProviderCredentialEntity() {
    }

    public static ProviderCredentialEntity create(
            String id,
            Long providerId,
            String credentialReference,
            byte[] encryptedSecret,
            byte[] encryptionNonce,
            int encryptionKeyVersion,
            String secretFingerprint,
            String secretHint
    ) {
        ProviderCredentialEntity entity = new ProviderCredentialEntity();
        entity.id = id;
        entity.providerId = providerId;
        entity.credentialReference = credentialReference;
        entity.encryptedSecret = encryptedSecret;
        entity.encryptionNonce = encryptionNonce;
        entity.encryptionKeyVersion = encryptionKeyVersion;
        entity.secretFingerprint = secretFingerprint;
        entity.secretHint = secretHint;
        entity.status = "ACTIVE";
        entity.revision = 0L;
        return entity;
    }

    public String getId() { return id; }
    public Long getProviderId() { return providerId; }
    public String getCredentialReference() { return credentialReference; }
    public byte[] getEncryptedSecret() { return encryptedSecret; }
    public byte[] getEncryptionNonce() { return encryptionNonce; }
    public Integer getEncryptionKeyVersion() { return encryptionKeyVersion; }
    public String getSecretFingerprint() { return secretFingerprint; }
    public String getSecretHint() { return secretHint; }
    public String getStatus() { return status; }
    public Long getRevision() { return revision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRotatedAt() { return rotatedAt; }
}
