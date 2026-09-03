package com.lumora.cloud.catalog.domain.entity.route;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumora.cloud.catalog.domain.model.ModelVersionValues.CostRatesValues;
import com.lumora.cloud.catalog.domain.model.ModelVersionValues.CostTimePricingRuleValues;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

@TableName("model_route_cost_pricing_rule")
public class ModelRouteCostRuleEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String routeId;
    private Integer ruleOrder;
    private String name;
    private Integer daysMask;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal inputCostPerMillion;
    private BigDecimal outputCostPerMillion;
    private BigDecimal cacheReadCostPerMillion;
    private BigDecimal cacheWriteCostPerMillion;

    public ModelRouteCostRuleEntity() {
    }

    public static ModelRouteCostRuleEntity create(
            String routeId,
            int ruleOrder,
            CostTimePricingRuleValues values
    ) {
        ModelRouteCostRuleEntity entity = new ModelRouteCostRuleEntity();
        entity.id = UUID.randomUUID().toString();
        entity.routeId = routeId;
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

    public static ModelRouteCostRuleEntity copyTo(String routeId, ModelRouteCostRuleEntity source) {
        ModelRouteCostRuleEntity entity = new ModelRouteCostRuleEntity();
        entity.id = UUID.randomUUID().toString();
        entity.routeId = routeId;
        entity.ruleOrder = source.ruleOrder;
        entity.name = source.name;
        entity.daysMask = source.daysMask;
        entity.startTime = source.startTime;
        entity.endTime = source.endTime;
        entity.inputCostPerMillion = source.inputCostPerMillion;
        entity.outputCostPerMillion = source.outputCostPerMillion;
        entity.cacheReadCostPerMillion = source.cacheReadCostPerMillion;
        entity.cacheWriteCostPerMillion = source.cacheWriteCostPerMillion;
        return entity;
    }

    public String getId() { return id; }
    public String getRouteId() { return routeId; }
    public Integer getRuleOrder() { return ruleOrder; }
    public String getName() { return name; }
    public Integer getDaysMask() { return daysMask; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public BigDecimal getInputCostPerMillion() { return inputCostPerMillion; }
    public BigDecimal getOutputCostPerMillion() { return outputCostPerMillion; }
    public BigDecimal getCacheReadCostPerMillion() { return cacheReadCostPerMillion; }
    public BigDecimal getCacheWriteCostPerMillion() { return cacheWriteCostPerMillion; }
}
