package com.lumora.cloud.billing.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lumora.cloud.billing.domain.BillingTypes.PlanStatus;
import com.lumora.cloud.billing.error.ApiException;
import com.lumora.cloud.billing.persistence.entity.BillingPlanEntity;
import com.lumora.cloud.billing.persistence.entity.PlanVersionEntity;
import com.lumora.cloud.billing.persistence.mapper.BillingPlanMapper;
import com.lumora.cloud.billing.persistence.mapper.PlanVersionMapper;
import com.lumora.cloud.billing.web.BillingWebContracts.CreatePlanRequest;
import com.lumora.cloud.billing.web.BillingWebContracts.CreatePlanVersionRequest;
import com.lumora.cloud.billing.web.BillingWebContracts.PlanResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class BillingCatalogService {

    private final BillingPlanMapper planMapper;
    private final PlanVersionMapper versionMapper;

    public BillingCatalogService(BillingPlanMapper planMapper, PlanVersionMapper versionMapper) {
        this.planMapper = planMapper;
        this.versionMapper = versionMapper;
    }

    @Transactional
    public PlanResponse create(CreatePlanRequest request) {
        String code = request.code().trim().toLowerCase(Locale.ROOT);
        if (planMapper.findByCodeForUpdate(code) != null) {
            throw duplicatePlan();
        }
        BigDecimal weeklyQuota = BillingAmounts.positive(request.weeklyQuota(), "weeklyQuota");
        BillingPlanEntity plan = BillingPlanEntity.create(code, request.name().trim(), trimToNull(request.description()));
        try {
            planMapper.insert(plan);
        } catch (DuplicateKeyException exception) {
            throw duplicatePlan();
        }
        PlanVersionEntity version = PlanVersionEntity.published(
                plan.getId(),
                1,
                request.monthlyPriceMinor(),
                request.currency(),
                weeklyQuota,
                Instant.now()
        );
        versionMapper.insert(version);
        return response(plan, version);
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listPublished() {
        return planMapper.selectList(Wrappers.<BillingPlanEntity>lambdaQuery()
                        .eq(BillingPlanEntity::getStatus, PlanStatus.ACTIVE.name())
                        .orderByAsc(BillingPlanEntity::getId))
                .stream()
                .map(plan -> {
                    PlanVersionEntity version = versionMapper.findLatestPublished(plan.getId());
                    return version == null ? null : response(plan, version);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public PlanResponse publishVersion(Long planId, CreatePlanVersionRequest request) {
        BillingPlanEntity plan = planMapper.findByIdForUpdate(planId);
        if (plan == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "套餐不存在");
        }
        if (!PlanStatus.ACTIVE.name().equals(plan.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_NOT_ACTIVE", "只有启用中的套餐可以发布新版本");
        }
        BigDecimal weeklyQuota = BillingAmounts.positive(request.weeklyQuota(), "weeklyQuota");
        int versionNo = versionMapper.maxVersionNo(planId) + 1;
        PlanVersionEntity version = PlanVersionEntity.published(
                planId,
                versionNo,
                request.monthlyPriceMinor(),
                request.currency(),
                weeklyQuota,
                Instant.now()
        );
        try {
            versionMapper.insert(version);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_VERSION_CONFLICT", "套餐版本已被其他操作发布");
        }
        return response(plan, version);
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> versions(Long planId) {
        BillingPlanEntity plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "套餐不存在");
        }
        return versionMapper.findAllByPlanId(planId).stream()
                .map(version -> response(plan, version))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse publishedVersion(Long planVersionId) {
        PlanVersionEntity version = versionMapper.findPublishedById(planVersionId);
        if (version == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PLAN_VERSION_NOT_FOUND", "套餐版本不存在或未发布");
        }
        BillingPlanEntity plan = planMapper.selectById(version.getPlanId());
        if (plan == null || !PlanStatus.ACTIVE.name().equals(plan.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_AVAILABLE", "套餐当前不可用");
        }
        return response(plan, version);
    }

    PlanResponse response(BillingPlanEntity plan, PlanVersionEntity version) {
        return new PlanResponse(
                plan.getId(), plan.getCode(), plan.getName(), plan.getDescription(),
                version.getId(), version.getVersionNo(), version.getMonthlyPriceMinor(),
                version.getCurrency(), version.getWeeklyQuota()
        );
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ApiException duplicatePlan() {
        return new ApiException(HttpStatus.CONFLICT, "PLAN_CODE_EXISTS", "套餐编码已经存在");
    }
}
