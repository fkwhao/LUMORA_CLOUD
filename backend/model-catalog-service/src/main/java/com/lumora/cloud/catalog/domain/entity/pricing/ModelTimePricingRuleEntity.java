package com.lumora.cloud.catalog.domain.entity.pricing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumora.cloud.catalog.domain.model.ModelVersionValues.CostTimePricingRuleValues;
import com.lumora.cloud.catalog.domain.model.ModelVersionValues.CostRatesValues;
import com.lumora.cloud.catalog.domain.model.ModelVersionValues.QuotaTimePricingRuleValues;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

@TableName("model_time_pricing_rule")
public class ModelTimePricingRuleEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String versionId;
    private String pricingScope;
    private Integer ruleOrder;
    private String name;
    private Integer daysMask;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal inputCostPerMillion;
    private BigDecimal outputCostPerMillion;
    private BigDecimal cacheReadCostPerMillion;
    private BigDecimal cacheWriteCostPerMillion;
    private BigDecimal quotaMultiplier;

    public ModelTimePricingRuleEntity() {
    }

    public static ModelTimePricingRuleEntity cost(
            String versionId,
            int ruleOrder,
            CostTimePricingRuleValues values
    ) {
        ModelTimePricingRuleEntity entity = new ModelTimePricingRuleEntity();
        entity.id = UUID.randomUUID().toString();
        entity.versionId = versionId;
        entity.pricingScope = "COST";
        entity.ruleOrder = ruleOrder;
        entity.name = values.name();
        entity.daysMask = values.daysMask();
        entity.startTime = values.startTime();
        entity.endTime = values.endTime();
        CostRatesValues rates = values.costRates();
        entity.inputCostPerMillion = rates.inputPerMillion();
        entity.outputCostPerMillion = rates.outputPerMillion();
        entity.cacheReadCostPerMillion = rates.cacheReadPerMillion();
        entity.cacheWriteCostPerMillion = rates.cacheWritePerMillion();
        return entity;
    }

    public static ModelTimePricingRuleEntity quota(
            String versionId,
            int ruleOrder,
            QuotaTimePricingRuleValues values
    ) {
        ModelTimePricingRuleEntity entity = new ModelTimePricingRuleEntity();
        entity.id = UUID.randomUUID().toString();
        entity.versionId = versionId;
        entity.pricingScope = "QUOTA";
        entity.ruleOrder = ruleOrder;
        entity.name = values.name();
        entity.daysMask = values.daysMask();
        entity.startTime = values.startTime();
        entity.endTime = values.endTime();
        entity.quotaMultiplier = values.quotaMultiplier();
        return entity;
    }

    public static ModelTimePricingRuleEntity copyTo(
            String versionId,
            ModelTimePricingRuleEntity source
    ) {
        ModelTimePricingRuleEntity entity = new ModelTimePricingRuleEntity();
        entity.id = UUID.randomUUID().toString();
        entity.versionId = versionId;
        entity.pricingScope = source.pricingScope;
        entity.ruleOrder = source.ruleOrder;
        entity.name = source.name;
        entity.daysMask = source.daysMask;
        entity.startTime = source.startTime;
        entity.endTime = source.endTime;
        entity.inputCostPerMillion = source.inputCostPerMillion;
        entity.outputCostPerMillion = source.outputCostPerMillion;
        entity.cacheReadCostPerMillion = source.cacheReadCostPerMillion;
        entity.cacheWriteCostPerMillion = source.cacheWriteCostPerMillion;
        entity.quotaMultiplier = source.quotaMultiplier;
        return entity;
    }

    public String getId() { return id; }
    public String getVersionId() { return versionId; }
    public String getPricingScope() { return pricingScope; }
    public Integer getRuleOrder() { return ruleOrder; }
    public String getName() { return name; }
    public Integer getDaysMask() { return daysMask; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public BigDecimal getInputCostPerMillion() { return inputCostPerMillion; }
    public BigDecimal getOutputCostPerMillion() { return outputCostPerMillion; }
    public BigDecimal getCacheReadCostPerMillion() { return cacheReadCostPerMillion; }
    public BigDecimal getCacheWriteCostPerMillion() { return cacheWriteCostPerMillion; }
    public BigDecimal getQuotaMultiplier() { return quotaMultiplier; }
}
