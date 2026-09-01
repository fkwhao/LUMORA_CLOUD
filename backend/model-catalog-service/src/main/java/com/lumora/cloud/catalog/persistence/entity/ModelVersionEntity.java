package com.lumora.cloud.catalog.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumora.cloud.catalog.domain.CatalogTypes.VersionStatus;
import com.lumora.cloud.catalog.domain.ModelVersionValues;
import com.lumora.cloud.catalog.domain.ModelVersionValues.CostTimePricingPolicyValues;
import com.lumora.cloud.catalog.domain.ModelVersionValues.QuotaTimePricingPolicyValues;

import java.math.BigDecimal;
import java.time.Instant;

@TableName("model_config_version")
public class ModelVersionEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String versionKey;
    private Long modelId;
    private Long providerId;
    private Integer versionNo;
    private String status;
    private Long revision;
    private String displayName;
    private String description;
    private String upstreamModel;
    private String protocolType;
    private String baseUrl;
    private String credentialReference;
    private Long contextWindow;
    private Long maxOutputTokens;
    private Boolean supportsReasoning;
    private Boolean supportsTools;
    private Boolean supportsVision;
    private Boolean supportsJson;
    private String costCurrency;
    private BigDecimal inputCostPerMillion;
    private BigDecimal outputCostPerMillion;
    private BigDecimal cacheReadCostPerMillion;
    private BigDecimal cacheWriteCostPerMillion;
    private Boolean costTimePricingEnabled;
    private String costTimePricingZone;
    private Boolean quotaTimePricingEnabled;
    private String quotaTimePricingZone;
    private BigDecimal inputQuotaPerMillion;
    private BigDecimal outputQuotaPerMillion;
    private BigDecimal cacheReadQuotaPerMillion;
    private BigDecimal cacheWriteQuotaPerMillion;
    private BigDecimal minimumRequestQuota;
    private BigDecimal defaultQuotaMultiplier;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;
    @TableField(exist = false)
    private Integer draftGuard;
    @TableField(exist = false)
    private Integer publishedGuard;
    @TableField(exist = false)
    private String modelCode;
    @TableField(exist = false)
    private String providerCode;
    @TableField(exist = false)
    private String activeCredentialReference;

    public ModelVersionEntity() {
    }

    public static ModelVersionEntity draft(
            String id,
            String versionKey,
            Long modelId,
            int versionNo,
            ProviderEntity provider,
            ModelVersionValues values
    ) {
        ModelVersionEntity entity = new ModelVersionEntity();
        entity.id = id;
        entity.versionKey = versionKey;
        entity.modelId = modelId;
        entity.versionNo = versionNo;
        entity.status = VersionStatus.DRAFT.name();
        entity.revision = 0L;
        entity.apply(provider, values);
        return entity;
    }

    public static ModelVersionEntity draftUpdate(
            ModelVersionEntity existing,
            ProviderEntity provider,
            ModelVersionValues values
    ) {
        ModelVersionEntity entity = new ModelVersionEntity();
        entity.id = existing.id;
        entity.versionKey = existing.versionKey;
        entity.modelId = existing.modelId;
        entity.versionNo = existing.versionNo;
        entity.status = existing.status;
        entity.revision = existing.revision;
        entity.apply(provider, values);
        return entity;
    }

    public static ModelVersionEntity copyAsDraft(
            String id,
            String versionKey,
            int versionNo,
            ModelVersionEntity published
    ) {
        ModelVersionEntity entity = new ModelVersionEntity();
        entity.id = id;
        entity.versionKey = versionKey;
        entity.modelId = published.modelId;
        entity.providerId = published.providerId;
        entity.versionNo = versionNo;
        entity.status = VersionStatus.DRAFT.name();
        entity.revision = 0L;
        entity.displayName = published.displayName;
        entity.description = published.description;
        entity.upstreamModel = published.upstreamModel;
        entity.protocolType = published.protocolType;
        entity.baseUrl = published.baseUrl;
        entity.credentialReference = published.credentialReference;
        entity.contextWindow = published.contextWindow;
        entity.maxOutputTokens = published.maxOutputTokens;
        entity.supportsReasoning = published.supportsReasoning;
        entity.supportsTools = published.supportsTools;
        entity.supportsVision = published.supportsVision;
        entity.supportsJson = published.supportsJson;
        entity.costCurrency = published.costCurrency;
        entity.inputCostPerMillion = published.inputCostPerMillion;
        entity.outputCostPerMillion = published.outputCostPerMillion;
        entity.cacheReadCostPerMillion = published.cacheReadCostPerMillion;
        entity.cacheWriteCostPerMillion = published.cacheWriteCostPerMillion;
        entity.costTimePricingEnabled = published.costTimePricingEnabled;
        entity.costTimePricingZone = published.costTimePricingZone;
        entity.quotaTimePricingEnabled = published.quotaTimePricingEnabled;
        entity.quotaTimePricingZone = published.quotaTimePricingZone;
        entity.inputQuotaPerMillion = published.inputQuotaPerMillion;
        entity.outputQuotaPerMillion = published.outputQuotaPerMillion;
        entity.cacheReadQuotaPerMillion = published.cacheReadQuotaPerMillion;
        entity.cacheWriteQuotaPerMillion = published.cacheWriteQuotaPerMillion;
        entity.minimumRequestQuota = published.minimumRequestQuota;
        entity.defaultQuotaMultiplier = published.defaultQuotaMultiplier;
        return entity;
    }

    private void apply(ProviderEntity provider, ModelVersionValues values) {
        providerId = provider.getId();
        displayName = values.displayName();
        description = values.description();
        upstreamModel = values.upstreamModel();
        protocolType = provider.getProtocolType();
        baseUrl = provider.getBaseUrl();
        credentialReference = provider.getCredentialReference();
        contextWindow = values.contextWindow();
        maxOutputTokens = values.maxOutputTokens();
        supportsReasoning = values.supportsReasoning();
        supportsTools = values.supportsTools();
        supportsVision = values.supportsVision();
        supportsJson = values.supportsJson();
        costCurrency = values.costCurrency();
        inputCostPerMillion = values.inputCostPerMillion();
        outputCostPerMillion = values.outputCostPerMillion();
        cacheReadCostPerMillion = values.cacheReadCostPerMillion();
        cacheWriteCostPerMillion = values.cacheWriteCostPerMillion();
        applyCostTimePricingPolicy(values.costTimePricingPolicy());
        inputQuotaPerMillion = values.inputQuotaPerMillion();
        outputQuotaPerMillion = values.outputQuotaPerMillion();
        cacheReadQuotaPerMillion = values.cacheReadQuotaPerMillion();
        cacheWriteQuotaPerMillion = values.cacheWriteQuotaPerMillion();
        minimumRequestQuota = values.minimumRequestQuota();
        applyQuotaTimePricingPolicy(values.quotaTimePricingPolicy());
    }

    private void applyCostTimePricingPolicy(CostTimePricingPolicyValues policy) {
        costTimePricingEnabled = policy != null;
        costTimePricingZone = policy == null ? null : policy.zoneId();
    }

    private void applyQuotaTimePricingPolicy(QuotaTimePricingPolicyValues policy) {
        quotaTimePricingEnabled = policy != null;
        quotaTimePricingZone = policy == null ? null : policy.zoneId();
        defaultQuotaMultiplier = policy == null ? BigDecimal.ONE : policy.defaultQuotaMultiplier();
    }

    public String getId() { return id; }
    public String getVersionKey() { return versionKey; }
    public Long getModelId() { return modelId; }
    public Long getProviderId() { return providerId; }
    public Integer getVersionNo() { return versionNo; }
    public String getStatus() { return status; }
    public Long getRevision() { return revision; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getUpstreamModel() { return upstreamModel; }
    public String getProtocolType() { return protocolType; }
    public String getBaseUrl() { return baseUrl; }
    public String getCredentialReference() { return credentialReference; }
    public Long getContextWindow() { return contextWindow; }
    public Long getMaxOutputTokens() { return maxOutputTokens; }
    public Boolean getSupportsReasoning() { return supportsReasoning; }
    public Boolean getSupportsTools() { return supportsTools; }
    public Boolean getSupportsVision() { return supportsVision; }
    public Boolean getSupportsJson() { return supportsJson; }
    public String getCostCurrency() { return costCurrency; }
    public BigDecimal getInputCostPerMillion() { return inputCostPerMillion; }
    public BigDecimal getOutputCostPerMillion() { return outputCostPerMillion; }
    public BigDecimal getCacheReadCostPerMillion() { return cacheReadCostPerMillion; }
    public BigDecimal getCacheWriteCostPerMillion() { return cacheWriteCostPerMillion; }
    public Boolean getCostTimePricingEnabled() { return costTimePricingEnabled; }
    public String getCostTimePricingZone() { return costTimePricingZone; }
    public Boolean getQuotaTimePricingEnabled() { return quotaTimePricingEnabled; }
    public String getQuotaTimePricingZone() { return quotaTimePricingZone; }
    public BigDecimal getInputQuotaPerMillion() { return inputQuotaPerMillion; }
    public BigDecimal getOutputQuotaPerMillion() { return outputQuotaPerMillion; }
    public BigDecimal getCacheReadQuotaPerMillion() { return cacheReadQuotaPerMillion; }
    public BigDecimal getCacheWriteQuotaPerMillion() { return cacheWriteQuotaPerMillion; }
    public BigDecimal getMinimumRequestQuota() { return minimumRequestQuota; }
    public BigDecimal getDefaultQuotaMultiplier() { return defaultQuotaMultiplier; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getModelCode() { return modelCode; }
    public String getProviderCode() { return providerCode; }
    public String getActiveCredentialReference() { return activeCredentialReference; }
}
