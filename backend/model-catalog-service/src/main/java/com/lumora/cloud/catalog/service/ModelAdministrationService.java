package com.lumora.cloud.catalog.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lumora.cloud.api.catalog.CatalogContracts.ModelCapabilities;
import com.lumora.cloud.api.catalog.CatalogContracts.QuotaRates;
import com.lumora.cloud.catalog.domain.CatalogTypes.ModelStatus;
import com.lumora.cloud.catalog.domain.ModelVersionValues;
import com.lumora.cloud.catalog.error.ApiException;
import com.lumora.cloud.catalog.persistence.entity.ModelDefinitionEntity;
import com.lumora.cloud.catalog.persistence.entity.ModelVersionEntity;
import com.lumora.cloud.catalog.persistence.entity.ProviderEntity;
import com.lumora.cloud.catalog.persistence.mapper.ModelDefinitionMapper;
import com.lumora.cloud.catalog.persistence.mapper.ModelVersionMapper;
import com.lumora.cloud.catalog.persistence.mapper.ProviderMapper;
import com.lumora.cloud.catalog.web.CatalogWebContracts.AdminModelResponse;
import com.lumora.cloud.catalog.web.CatalogWebContracts.CostRates;
import com.lumora.cloud.catalog.web.CatalogWebContracts.CreateModelRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.ModelVersionResponse;
import com.lumora.cloud.catalog.web.CatalogWebContracts.PublishDraftRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.UpdateDraftRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.UpdateModelStatusRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ModelAdministrationService {

    private final ModelDefinitionMapper modelMapper;
    private final ModelVersionMapper versionMapper;
    private final ProviderMapper providerMapper;
    private final ProviderService providerService;
    private final CatalogInputMapper inputMapper;
    private final TimePricingPolicyService timePricingPolicyService;
    private final PublishedCatalogCache cache;

    public ModelAdministrationService(
            ModelDefinitionMapper modelMapper,
            ModelVersionMapper versionMapper,
            ProviderMapper providerMapper,
            ProviderService providerService,
            CatalogInputMapper inputMapper,
            TimePricingPolicyService timePricingPolicyService,
            PublishedCatalogCache cache
    ) {
        this.modelMapper = modelMapper;
        this.versionMapper = versionMapper;
        this.providerMapper = providerMapper;
        this.providerService = providerService;
        this.inputMapper = inputMapper;
        this.timePricingPolicyService = timePricingPolicyService;
        this.cache = cache;
    }

    @Transactional
    public AdminModelResponse create(CreateModelRequest request) {
        String code = request.code().trim().toLowerCase(Locale.ROOT);
        if (modelMapper.findByCodeForUpdate(code) != null) {
            throw duplicateCode();
        }
        ProviderEntity provider = providerService.requireActiveForUpdate(request.providerId());
        ModelVersionValues values = inputMapper.values(request.version());
        validateHostedWebSearch(provider, values);
        ModelDefinitionEntity model = ModelDefinitionEntity.create(code);
        try {
            modelMapper.insert(model);
        } catch (DuplicateKeyException exception) {
            throw duplicateCode();
        }
        ModelVersionEntity draft = ModelVersionEntity.draft(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), model.getId(), 1, provider, values
        );
        versionMapper.insert(draft);
        timePricingPolicyService.replace(
                draft.getId(), values.costTimePricingPolicy(), values.quotaTimePricingPolicy()
        );
        return response(modelMapper.selectById(model.getId()), draft, null);
    }

    @Transactional
    public AdminModelResponse createDraft(Long modelId) {
        ModelDefinitionEntity model = requireModelForUpdate(modelId);
        if (versionMapper.findDraftForUpdate(modelId) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "MODEL_DRAFT_EXISTS", "该模型已经有一个待编辑草稿");
        }
        ModelVersionEntity published = versionMapper.findPublishedForUpdate(modelId);
        if (published == null) {
            throw new ApiException(HttpStatus.CONFLICT, "MODEL_NOT_PUBLISHED", "该模型还没有可复制的发布版本");
        }
        int versionNo = versionMapper.maxVersionNo(modelId) + 1;
        ModelVersionEntity draft = ModelVersionEntity.copyAsDraft(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), versionNo, published
        );
        try {
            versionMapper.insert(draft);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "MODEL_DRAFT_CONFLICT", "草稿已被其他操作创建");
        }
        timePricingPolicyService.copy(published.getId(), draft.getId());
        modelMapper.touch(modelId);
        return response(modelMapper.selectById(modelId), draft, published);
    }

    @Transactional
    public AdminModelResponse updateDraft(Long modelId, UpdateDraftRequest request) {
        ModelDefinitionEntity model = requireModelForUpdate(modelId);
        ModelVersionEntity draft = requireDraftForUpdate(modelId);
        if (draft.getRevision() != request.expectedRevision()) {
            throw draftConflict();
        }
        ProviderEntity provider = providerService.requireActiveForUpdate(request.providerId());
        ModelVersionValues values = inputMapper.values(request.version());
        validateHostedWebSearch(provider, values);
        ModelVersionEntity update = ModelVersionEntity.draftUpdate(draft, provider, values);
        if (versionMapper.updateDraftOptimistic(update, request.expectedRevision()) != 1) {
            throw draftConflict();
        }
        timePricingPolicyService.replace(
                draft.getId(), values.costTimePricingPolicy(), values.quotaTimePricingPolicy()
        );
        modelMapper.touch(modelId);
        return response(
                modelMapper.selectById(modelId), versionMapper.findDraft(modelId), versionMapper.findPublished(modelId)
        );
    }

    @Transactional
    public void discardDraft(Long modelId, long expectedRevision) {
        requireModelForUpdate(modelId);
        ModelVersionEntity draft = requireDraftForUpdate(modelId);
        if (draft.getRevision() != expectedRevision) {
            throw draftConflict();
        }
        ModelVersionEntity published = versionMapper.findPublishedForUpdate(modelId);
        if (versionMapper.deleteDraftOptimistic(draft.getId(), modelId, expectedRevision) != 1) {
            throw draftConflict();
        }
        if (published == null) {
            if (modelMapper.deleteById(modelId) != 1) {
                throw modelConflict();
            }
            return;
        }
        modelMapper.touch(modelId);
    }

    @Transactional
    public AdminModelResponse publishDraft(Long modelId, PublishDraftRequest request) {
        ModelDefinitionEntity model = requireModelForUpdate(modelId);
        if (!ModelStatus.ACTIVE.name().equals(model.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "MODEL_DISABLED", "停用模型不能发布新版本");
        }
        ModelVersionEntity draft = requireDraftForUpdate(modelId);
        if (draft.getRevision() != request.expectedRevision()) {
            throw draftConflict();
        }
        ProviderEntity provider = providerMapper.findByIdForUpdate(draft.getProviderId());
        if (provider == null || !"ACTIVE".equals(provider.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "PROVIDER_DISABLED", "草稿引用的供应商不可用");
        }
        if (!provider.getProtocolType().equals(draft.getProtocolType())
                || !provider.getBaseUrl().equals(draft.getBaseUrl())) {
            throw new ApiException(HttpStatus.CONFLICT, "MODEL_DRAFT_PROVIDER_STALE",
                    "供应商协议或 API 地址已变更，请先重新保存草稿再发布");
        }
        validateHostedWebSearch(provider, draft.getSupportsWebSearch());
        versionMapper.findPublishedForUpdate(modelId);
        versionMapper.archivePublished(modelId);
        if (versionMapper.publishDraft(draft.getId(), request.expectedRevision(), Instant.now()) != 1) {
            throw draftConflict();
        }
        modelMapper.touch(modelId);
        cache.evictAfterCommit();
        return response(modelMapper.selectById(modelId), null, versionMapper.findPublished(modelId));
    }

    @Transactional
    public AdminModelResponse updateStatus(Long modelId, UpdateModelStatusRequest request) {
        ModelDefinitionEntity model = requireModelForUpdate(modelId);
        if (model.getRevision() != request.expectedRevision()) {
            throw modelConflict();
        }
        ModelStatus status = ModelStatus.valueOf(request.status());
        if (modelMapper.updateStatusOptimistic(modelId, request.expectedRevision(), status.name()) != 1) {
            throw modelConflict();
        }
        cache.evictAfterCommit();
        return response(
                modelMapper.selectById(modelId), versionMapper.findDraft(modelId), versionMapper.findPublished(modelId)
        );
    }

    @Transactional(readOnly = true)
    public List<AdminModelResponse> list() {
        return modelMapper.selectList(Wrappers.<ModelDefinitionEntity>lambdaQuery()
                        .orderByAsc(ModelDefinitionEntity::getId))
                .stream()
                .map(model -> response(model, versionMapper.findDraft(model.getId()),
                        versionMapper.findPublished(model.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ModelVersionResponse> history(Long modelId) {
        if (modelMapper.selectById(modelId) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MODEL_NOT_FOUND", "模型不存在");
        }
        return versionMapper.findAllByModelId(modelId).stream()
                .map(this::version)
                .toList();
    }

    private ModelDefinitionEntity requireModelForUpdate(Long modelId) {
        ModelDefinitionEntity model = modelMapper.findByIdForUpdate(modelId);
        if (model == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MODEL_NOT_FOUND", "模型不存在");
        }
        return model;
    }

    private ModelVersionEntity requireDraftForUpdate(Long modelId) {
        ModelVersionEntity draft = versionMapper.findDraftForUpdate(modelId);
        if (draft == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MODEL_DRAFT_NOT_FOUND", "模型草稿不存在");
        }
        return draft;
    }

    private AdminModelResponse response(
            ModelDefinitionEntity model,
            ModelVersionEntity draft,
            ModelVersionEntity published
    ) {
        return new AdminModelResponse(
                model.getId(), model.getCode(), model.getStatus(), model.getRevision(), version(draft),
                version(published), model.getCreatedAt(), model.getUpdatedAt()
        );
    }

    private ModelVersionResponse version(ModelVersionEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ModelVersionResponse(
                entity.getId(), entity.getVersionKey(), entity.getVersionNo(), entity.getStatus(),
                entity.getRevision(), entity.getProviderId(), entity.getDisplayName(), entity.getDescription(),
                entity.getUpstreamModel(), entity.getProtocolType(), entity.getBaseUrl(),
                entity.getCredentialReference(), new ModelCapabilities(
                        entity.getContextWindow(), entity.getMaxOutputTokens(), entity.getSupportsReasoning(),
                        entity.getSupportsTools(), entity.getSupportsVision(), entity.getSupportsJson(),
                        entity.getSupportsWebSearch()
                ), entity.getCostCurrency(), costRates(
                        entity.getInputCostPerMillion(), entity.getCacheReadCostPerMillion(),
                        entity.getCacheWriteCostPerMillion(), entity.getOutputCostPerMillion()
                ), timePricingPolicyService.adminCostPolicy(entity), new QuotaRates(
                        entity.getInputQuotaPerMillion(), entity.getCacheReadQuotaPerMillion(),
                        entity.getCacheWriteQuotaPerMillion(), entity.getOutputQuotaPerMillion(),
                        entity.getMinimumRequestQuota()
                ), timePricingPolicyService.adminQuotaPolicy(entity), entity.getPublishedAt(),
                entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    private CostRates costRates(
            java.math.BigDecimal input,
            java.math.BigDecimal cachedInput,
            java.math.BigDecimal cacheCreationInput,
            java.math.BigDecimal output
    ) {
        return new CostRates(input, cachedInput, cacheCreationInput, output);
    }

    private ApiException duplicateCode() {
        return new ApiException(HttpStatus.CONFLICT, "MODEL_CODE_EXISTS", "模型编码已经存在");
    }

    private void validateHostedWebSearch(ProviderEntity provider, ModelVersionValues values) {
        validateHostedWebSearch(provider, values.supportsWebSearch());
    }

    private void validateHostedWebSearch(ProviderEntity provider, boolean enabled) {
        if (enabled && !"ANTHROPIC".equals(provider.getProtocolType())
                && !"RESPONSES".equals(provider.getProtocolType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HOSTED_WEB_SEARCH_PROTOCOL_UNSUPPORTED",
                    "供应商托管 Web Search 仅支持 Anthropic Messages 或 Responses 协议");
        }
    }

    private ApiException draftConflict() {
        return new ApiException(HttpStatus.CONFLICT, "MODEL_DRAFT_REVISION_CONFLICT",
                "模型草稿已被其他操作更新，请刷新后重试");
    }

    private ApiException modelConflict() {
        return new ApiException(HttpStatus.CONFLICT, "MODEL_REVISION_CONFLICT",
                "模型状态已被其他操作更新，请刷新后重试");
    }
}
