package com.lumora.cloud.catalog.domain.entity.route;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumora.cloud.catalog.domain.entity.provider.ProviderEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@TableName("model_upstream_route")
public class ModelRouteEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String versionId;
    private Long providerId;
    private String routeName;
    private String upstreamModel;
    private String protocolType;
    private String baseUrl;
    private String credentialReference;
    @TableField("route_priority")
    private Integer routePriority;
    @TableField("route_weight")
    private Integer routeWeight;
    private Integer maxConcurrency;
    private Integer requestsPerMinute;
    private Long tokensPerMinute;
    private Boolean failoverEnabled;
    private Boolean circuitBreakerEnabled;
    private String status;
    private Boolean isPrimary;
    private String costCurrency;
    private BigDecimal inputCostPerMillion;
    private BigDecimal outputCostPerMillion;
    private BigDecimal cacheReadCostPerMillion;
    private BigDecimal cacheWriteCostPerMillion;
    private Boolean costTimePricingEnabled;
    private String costTimePricingZone;
    private Long revision;
    private Instant createdAt;
    private Instant updatedAt;

    @TableField(exist = false)
    private String providerCode;
    @TableField(exist = false)
    private String providerName;
    @TableField(exist = false)
    private String providerStatus;
    @TableField(exist = false)
    private String activeCredentialReference;
    @TableField(exist = false)
    private Integer accountMaxConcurrency;
    @TableField(exist = false)
    private Integer accountRequestsPerMinute;
    @TableField(exist = false)
    private Long accountTokensPerMinute;

    public ModelRouteEntity() {
    }

    public static ModelRouteEntity create(
            String versionId,
            ProviderEntity provider,
            String routeName,
            String upstreamModel,
            int priority,
            int weight,
            Integer maxConcurrency,
            Integer requestsPerMinute,
            Long tokensPerMinute,
            boolean failoverEnabled,
            boolean circuitBreakerEnabled,
            String status,
            boolean primary,
            String costCurrency,
            BigDecimal inputCost,
            BigDecimal cachedInputCost,
            BigDecimal cacheCreationCost,
            BigDecimal outputCost,
            String costTimePricingZone
    ) {
        ModelRouteEntity entity = new ModelRouteEntity();
        entity.id = UUID.randomUUID().toString();
        entity.versionId = versionId;
        entity.apply(provider, routeName, upstreamModel, priority, weight, maxConcurrency,
                requestsPerMinute, tokensPerMinute, failoverEnabled, circuitBreakerEnabled,
                status, costCurrency, inputCost, cachedInputCost, cacheCreationCost,
                outputCost, costTimePricingZone);
        entity.isPrimary = primary;
        entity.revision = 0L;
        return entity;
    }

    public static ModelRouteEntity copyTo(String versionId, ModelRouteEntity source) {
        ModelRouteEntity entity = new ModelRouteEntity();
        entity.id = UUID.randomUUID().toString();
        entity.versionId = versionId;
        entity.providerId = source.providerId;
        entity.routeName = source.routeName;
        entity.upstreamModel = source.upstreamModel;
        entity.protocolType = source.protocolType;
        entity.baseUrl = source.baseUrl;
        entity.credentialReference = source.credentialReference;
        entity.routePriority = source.routePriority;
        entity.routeWeight = source.routeWeight;
        entity.maxConcurrency = source.maxConcurrency;
        entity.requestsPerMinute = source.requestsPerMinute;
        entity.tokensPerMinute = source.tokensPerMinute;
        entity.failoverEnabled = source.failoverEnabled;
        entity.circuitBreakerEnabled = source.circuitBreakerEnabled;
        entity.status = source.status;
        entity.isPrimary = source.isPrimary;
        entity.costCurrency = source.costCurrency;
        entity.inputCostPerMillion = source.inputCostPerMillion;
        entity.outputCostPerMillion = source.outputCostPerMillion;
        entity.cacheReadCostPerMillion = source.cacheReadCostPerMillion;
        entity.cacheWriteCostPerMillion = source.cacheWriteCostPerMillion;
        entity.costTimePricingEnabled = source.costTimePricingEnabled;
        entity.costTimePricingZone = source.costTimePricingZone;
        entity.revision = 0L;
        return entity;
    }

    public void apply(
            ProviderEntity provider,
            String routeName,
            String upstreamModel,
            int priority,
            int weight,
            Integer maxConcurrency,
            Integer requestsPerMinute,
            Long tokensPerMinute,
            boolean failoverEnabled,
            boolean circuitBreakerEnabled,
            String status,
            String costCurrency,
            BigDecimal inputCost,
            BigDecimal cachedInputCost,
            BigDecimal cacheCreationCost,
            BigDecimal outputCost,
            String costTimePricingZone
    ) {
        this.providerId = provider.getId();
        this.routeName = routeName;
        this.upstreamModel = upstreamModel;
        this.protocolType = provider.getProtocolType();
        this.baseUrl = provider.getBaseUrl();
        this.credentialReference = provider.getCredentialReference();
        this.routePriority = priority;
        this.routeWeight = weight;
        this.maxConcurrency = maxConcurrency;
        this.requestsPerMinute = requestsPerMinute;
        this.tokensPerMinute = tokensPerMinute;
        this.failoverEnabled = failoverEnabled;
        this.circuitBreakerEnabled = circuitBreakerEnabled;
        this.status = status;
        this.costCurrency = costCurrency;
        this.inputCostPerMillion = inputCost;
        this.cacheReadCostPerMillion = cachedInputCost;
        this.cacheWriteCostPerMillion = cacheCreationCost;
        this.outputCostPerMillion = outputCost;
        this.costTimePricingEnabled = costTimePricingZone != null;
        this.costTimePricingZone = costTimePricingZone;
    }

    public String getId() { return id; }
    public String getVersionId() { return versionId; }
    public Long getProviderId() { return providerId; }
    public String getRouteName() { return routeName; }
    public String getUpstreamModel() { return upstreamModel; }
    public String getProtocolType() { return protocolType; }
    public String getBaseUrl() { return baseUrl; }
    public String getCredentialReference() { return credentialReference; }
    public Integer getPriority() { return routePriority; }
    public Integer getWeight() { return routeWeight; }
    public Integer getMaxConcurrency() { return maxConcurrency; }
    public Integer getRequestsPerMinute() { return requestsPerMinute; }
    public Long getTokensPerMinute() { return tokensPerMinute; }
    public Boolean getFailoverEnabled() { return failoverEnabled; }
    public Boolean getCircuitBreakerEnabled() { return circuitBreakerEnabled; }
    public String getStatus() { return status; }
    public Boolean getIsPrimary() { return isPrimary; }
    public String getCostCurrency() { return costCurrency; }
    public BigDecimal getInputCostPerMillion() { return inputCostPerMillion; }
    public BigDecimal getOutputCostPerMillion() { return outputCostPerMillion; }
    public BigDecimal getCacheReadCostPerMillion() { return cacheReadCostPerMillion; }
    public BigDecimal getCacheWriteCostPerMillion() { return cacheWriteCostPerMillion; }
    public Boolean getCostTimePricingEnabled() { return costTimePricingEnabled; }
    public String getCostTimePricingZone() { return costTimePricingZone; }
    public Long getRevision() { return revision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getProviderCode() { return providerCode; }
    public String getProviderName() { return providerName; }
    public String getProviderStatus() { return providerStatus; }
    public String getActiveCredentialReference() { return activeCredentialReference; }
    public Integer getAccountMaxConcurrency() { return accountMaxConcurrency; }
    public Integer getAccountRequestsPerMinute() { return accountRequestsPerMinute; }
    public Long getAccountTokensPerMinute() { return accountTokensPerMinute; }
}
