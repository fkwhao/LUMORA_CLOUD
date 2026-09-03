package com.lumora.cloud.catalog.domain.entity.provider;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumora.cloud.catalog.domain.enums.ProviderStatus;

import java.time.Instant;

@TableName("model_provider")
public class ProviderEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String protocolType;
    private String baseUrl;
    private String credentialReference;
    private Integer maxConcurrency;
    private Integer requestsPerMinute;
    private Long tokensPerMinute;
    private String status;
    private Long revision;
    private Instant createdAt;
    private Instant updatedAt;

    public ProviderEntity() {
    }

    public static ProviderEntity create(
            String code,
            String name,
            String protocolType,
            String baseUrl,
            String credentialReference,
            Integer maxConcurrency,
            Integer requestsPerMinute,
            Long tokensPerMinute
    ) {
        ProviderEntity entity = new ProviderEntity();
        entity.code = code;
        entity.name = name;
        entity.protocolType = protocolType;
        entity.baseUrl = baseUrl;
        entity.credentialReference = credentialReference;
        entity.maxConcurrency = maxConcurrency;
        entity.requestsPerMinute = requestsPerMinute;
        entity.tokensPerMinute = tokensPerMinute;
        entity.status = ProviderStatus.ACTIVE.name();
        entity.revision = 0L;
        return entity;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getProtocolType() { return protocolType; }
    public String getBaseUrl() { return baseUrl; }
    public String getCredentialReference() { return credentialReference; }
    public Integer getMaxConcurrency() { return maxConcurrency; }
    public Integer getRequestsPerMinute() { return requestsPerMinute; }
    public Long getTokensPerMinute() { return tokensPerMinute; }
    public String getStatus() { return status; }
    public Long getRevision() { return revision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
