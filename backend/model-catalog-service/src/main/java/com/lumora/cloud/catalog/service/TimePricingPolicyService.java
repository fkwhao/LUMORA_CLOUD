package com.lumora.cloud.catalog.service;

import com.lumora.cloud.api.catalog.CatalogContracts;
import com.lumora.cloud.catalog.domain.ModelVersionValues.CostTimePricingPolicyValues;
import com.lumora.cloud.catalog.domain.ModelVersionValues.QuotaTimePricingPolicyValues;
import com.lumora.cloud.catalog.persistence.entity.ModelTimePricingRuleEntity;
import com.lumora.cloud.catalog.persistence.entity.ModelVersionEntity;
import com.lumora.cloud.catalog.persistence.mapper.ModelTimePricingRuleMapper;
import com.lumora.cloud.catalog.web.CatalogWebContracts;
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

    public CatalogWebContracts.CostTimePricingPolicy adminCostPolicy(ModelVersionEntity version) {
        if (!Boolean.TRUE.equals(version.getCostTimePricingEnabled())) {
            return null;
        }
        List<CatalogWebContracts.CostTimePricingRule> rules = ruleMapper
                .findByVersionIdAndScope(version.getId(), "COST").stream()
                .map(rule -> new CatalogWebContracts.CostTimePricingRule(
                        rule.getName(), daysOfWeek(rule.getDaysMask()), rule.getStartTime(), rule.getEndTime(),
                        new CatalogWebContracts.CostRates(
                                rule.getInputCostPerMillion(), rule.getCacheReadCostPerMillion(),
                                rule.getCacheWriteCostPerMillion(), rule.getOutputCostPerMillion()
                        )
                ))
                .toList();
        return new CatalogWebContracts.CostTimePricingPolicy(version.getCostTimePricingZone(), rules);
    }

    public CatalogWebContracts.QuotaTimePricingPolicy adminQuotaPolicy(ModelVersionEntity version) {
        if (!Boolean.TRUE.equals(version.getQuotaTimePricingEnabled())) {
            return null;
        }
        List<CatalogWebContracts.QuotaTimePricingRule> rules = ruleMapper
                .findByVersionIdAndScope(version.getId(), "QUOTA").stream()
                .map(rule -> new CatalogWebContracts.QuotaTimePricingRule(
                        rule.getName(), daysOfWeek(rule.getDaysMask()), rule.getStartTime(), rule.getEndTime(),
                        rule.getQuotaMultiplier()
                ))
                .toList();
        return new CatalogWebContracts.QuotaTimePricingPolicy(
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
