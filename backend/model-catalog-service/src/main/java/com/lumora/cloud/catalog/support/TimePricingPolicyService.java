package com.lumora.cloud.catalog.support;

import com.lumora.cloud.api.catalog.CatalogContracts;
import com.lumora.cloud.catalog.domain.model.ModelVersionValues.CostTimePricingPolicyValues;
import com.lumora.cloud.catalog.domain.model.ModelVersionValues.QuotaTimePricingPolicyValues;
import com.lumora.cloud.catalog.domain.entity.pricing.ModelTimePricingRuleEntity;
import com.lumora.cloud.catalog.domain.entity.model.ModelVersionEntity;
import com.lumora.cloud.catalog.mapper.pricing.ModelTimePricingRuleMapper;
import com.lumora.cloud.catalog.domain.vo.pricing.CostRates;
import com.lumora.cloud.catalog.domain.vo.pricing.CostTimePricingPolicy;
import com.lumora.cloud.catalog.domain.vo.pricing.CostTimePricingRule;
import com.lumora.cloud.catalog.domain.vo.pricing.QuotaTimePricingPolicy;
import com.lumora.cloud.catalog.domain.vo.pricing.QuotaTimePricingRule;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

@Service
public class TimePricingPolicyService {

    private final ModelTimePricingRuleMapper ruleMapper;

    public TimePricingPolicyService(ModelTimePricingRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    public void replace(
            String versionId,
            CostTimePricingPolicyValues costPolicy,
            QuotaTimePricingPolicyValues quotaPolicy
    ) {
        ruleMapper.deleteByVersionId(versionId);
        if (costPolicy != null) {
            for (int index = 0; index < costPolicy.rules().size(); index++) {
                ruleMapper.insert(ModelTimePricingRuleEntity.cost(versionId, index, costPolicy.rules().get(index)));
            }
        }
        if (quotaPolicy != null) {
            for (int index = 0; index < quotaPolicy.rules().size(); index++) {
                ruleMapper.insert(ModelTimePricingRuleEntity.quota(versionId, index, quotaPolicy.rules().get(index)));
            }
        }
    }

    public void copy(String sourceVersionId, String targetVersionId) {
        List<ModelTimePricingRuleEntity> source = ruleMapper.findByVersionId(sourceVersionId);
        for (ModelTimePricingRuleEntity rule : source) {
            ruleMapper.insert(ModelTimePricingRuleEntity.copyTo(targetVersionId, rule));
        }
    }

    public CostTimePricingPolicy adminCostPolicy(ModelVersionEntity version) {
        if (!Boolean.TRUE.equals(version.getCostTimePricingEnabled())) {
            return null;
        }
        List<CostTimePricingRule> rules = ruleMapper
                .findByVersionIdAndScope(version.getId(), "COST").stream()
                .map(rule -> new CostTimePricingRule(
                        rule.getName(), daysOfWeek(rule.getDaysMask()), rule.getStartTime(), rule.getEndTime(),
                        new CostRates(
                                rule.getInputCostPerMillion(), rule.getCacheReadCostPerMillion(),
                                rule.getCacheWriteCostPerMillion(), rule.getOutputCostPerMillion()
                        )
                ))
                .toList();
        return new CostTimePricingPolicy(version.getCostTimePricingZone(), rules);
    }

    public QuotaTimePricingPolicy adminQuotaPolicy(ModelVersionEntity version) {
        if (!Boolean.TRUE.equals(version.getQuotaTimePricingEnabled())) {
            return null;
        }
        List<QuotaTimePricingRule> rules = ruleMapper
                .findByVersionIdAndScope(version.getId(), "QUOTA").stream()
                .map(rule -> new QuotaTimePricingRule(
                        rule.getName(), daysOfWeek(rule.getDaysMask()), rule.getStartTime(), rule.getEndTime(),
                        rule.getQuotaMultiplier()
                ))
                .toList();
        return new QuotaTimePricingPolicy(
                version.getQuotaTimePricingZone(), version.getDefaultQuotaMultiplier(), rules
        );
    }

    public CatalogContracts.CostTimePricingPolicy resolvedCostPolicy(ModelVersionEntity version) {
        if (!Boolean.TRUE.equals(version.getCostTimePricingEnabled())) {
            return null;
        }
        List<CatalogContracts.CostTimePricingRule> rules = ruleMapper
                .findByVersionIdAndScope(version.getId(), "COST").stream()
                .map(rule -> new CatalogContracts.CostTimePricingRule(
                        rule.getName(), daysOfWeek(rule.getDaysMask()), rule.getStartTime(), rule.getEndTime(),
                        new CatalogContracts.CostRates(
                                rule.getInputCostPerMillion(), rule.getCacheReadCostPerMillion(),
                                rule.getCacheWriteCostPerMillion(), rule.getOutputCostPerMillion()
                        )
                ))
                .toList();
        return new CatalogContracts.CostTimePricingPolicy(version.getCostTimePricingZone(), rules);
    }

    public CatalogContracts.QuotaTimePricingPolicy resolvedQuotaPolicy(ModelVersionEntity version) {
        if (!Boolean.TRUE.equals(version.getQuotaTimePricingEnabled())) {
            return null;
        }
        List<CatalogContracts.QuotaTimePricingRule> rules = ruleMapper
                .findByVersionIdAndScope(version.getId(), "QUOTA").stream()
                .map(rule -> new CatalogContracts.QuotaTimePricingRule(
                        rule.getName(), daysOfWeek(rule.getDaysMask()), rule.getStartTime(), rule.getEndTime(),
                        rule.getQuotaMultiplier()
                ))
                .toList();
        return new CatalogContracts.QuotaTimePricingPolicy(
                version.getQuotaTimePricingZone(), version.getDefaultQuotaMultiplier(), rules
        );
    }

    private List<DayOfWeek> daysOfWeek(int mask) {
        List<DayOfWeek> days = new ArrayList<>(7);
        for (DayOfWeek day : DayOfWeek.values()) {
            if ((mask & (1 << (day.getValue() - 1))) != 0) {
                days.add(day);
            }
        }
        return List.copyOf(days);
    }
}
