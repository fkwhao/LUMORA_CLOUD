package com.lumora.cloud.billing.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lumora.cloud.billing.domain.enums.PlanStatus;
import com.lumora.cloud.billing.error.ApiException;
import com.lumora.cloud.billing.domain.entity.plan.BillingPlanEntity;
import com.lumora.cloud.billing.domain.entity.plan.PlanVersionEntity;
import com.lumora.cloud.billing.mapper.plan.BillingPlanMapper;
import com.lumora.cloud.billing.mapper.plan.PlanVersionMapper;
import com.lumora.cloud.billing.mapper.plan.PlanVersionModelMapper;
import com.lumora.cloud.billing.domain.entity.plan.PlanVersionModelEntity;
import com.lumora.cloud.billing.domain.dto.plan.CreatePlanRequest;
import com.lumora.cloud.billing.domain.dto.plan.CreatePlanVersionRequest;
import com.lumora.cloud.billing.domain.vo.plan.PlanResponse;
import com.lumora.cloud.billing.service.IBillingCatalogService;
import com.lumora.cloud.billing.support.PlanModelSelectionService;
import com.lumora.cloud.billing.utils.BillingAmounts;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BillingCatalogServiceImpl implements IBillingCatalogService {

    private final BillingPlanMapper planMapper;
    private final PlanVersionMapper versionMapper;
    private final PlanVersionModelMapper versionModelMapper;
    private final PlanModelSelectionService modelSelection;

    @Transactional
    public PlanResponse create(CreatePlanRequest request) {
        String code = request.code().trim().toLowerCase(Locale.ROOT);
        if (planMapper.findByCodeForUpdate(code) != null) {
            throw duplicatePlan();
        }
        BigDecimal weeklyQuota = BillingAmounts.positive(request.weeklyQuota(), "weeklyQuota");
        List<String> modelCodes = modelSelection.normalizeAndValidate(request.modelCodes());
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
        insertModels(version.getId(), modelCodes);
        return response(plan, version);
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listPublished() {
        java.util.Set<String> available = modelSelection.availableModelCodes();
        return listAllPublished().stream().filter(plan -> hasAvailableModel(plan, available)).toList();
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listAllPublished() {
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
        // Catalog validation is a remote control-plane call. Complete it before
        // taking the plan row lock so a slow catalog cannot extend lock time.
        List<String> modelCodes = modelSelection.normalizeAndValidate(request.modelCodes());
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
            insertModels(version.getId(), modelCodes);
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

    @Transactional(readOnly = true)
    public PlanResponse purchasableVersion(Long planVersionId) {
        PlanResponse plan = publishedVersion(planVersionId);
        if (!hasAvailableModel(plan, modelSelection.availableModelCodes())) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_HAS_NO_AVAILABLE_MODELS",
                    "该套餐暂时没有可用模型，已暂停购买");
        }
        return plan;
    }

    private boolean hasAvailableModel(PlanResponse plan, java.util.Set<String> available) {
        return "ALL_PUBLISHED_LEGACY".equals(plan.modelAccessMode())
                ? !available.isEmpty() : plan.modelCodes().stream().anyMatch(available::contains);
    }

    PlanResponse response(BillingPlanEntity plan, PlanVersionEntity version) {
        return new PlanResponse(
                plan.getId(), plan.getCode(), plan.getName(), plan.getDescription(),
                version.getId(), version.getVersionNo(), version.getMonthlyPriceMinor(),
                version.getCurrency(), version.getWeeklyQuota(), version.getModelAccessMode(),
                versionModelMapper.findModelCodes(version.getId())
        );
    }

    private void insertModels(Long planVersionId, List<String> modelCodes) {
        for (int index = 0; index < modelCodes.size(); index++) {
            versionModelMapper.insert(PlanVersionModelEntity.create(planVersionId, modelCodes.get(index), index));
        }
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
