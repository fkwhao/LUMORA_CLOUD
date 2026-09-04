package com.lumora.cloud.catalog.support;

import com.lumora.cloud.api.catalog.CatalogContracts;
import com.lumora.cloud.catalog.domain.model.ModelVersionValues;
import com.lumora.cloud.catalog.domain.model.ModelVersionValues.CostTimePricingPolicyValues;
import com.lumora.cloud.catalog.error.ApiException;
import com.lumora.cloud.catalog.domain.entity.route.ModelRouteCostRuleEntity;
import com.lumora.cloud.catalog.domain.entity.route.ModelRouteEntity;
import com.lumora.cloud.catalog.domain.entity.model.ModelVersionEntity;
import com.lumora.cloud.catalog.domain.entity.provider.ProviderEntity;
import com.lumora.cloud.catalog.mapper.route.ModelRouteCostRuleMapper;
import com.lumora.cloud.catalog.mapper.route.ModelRouteMapper;
import com.lumora.cloud.catalog.domain.vo.pricing.CostRates;
import com.lumora.cloud.catalog.domain.vo.pricing.CostTimePricingPolicy;
import com.lumora.cloud.catalog.domain.vo.pricing.CostTimePricingRule;
import com.lumora.cloud.catalog.domain.dto.route.ModelRouteInput;
import com.lumora.cloud.catalog.domain.vo.route.ModelRouteResponse;
import com.lumora.cloud.catalog.utils.CatalogAmounts;
import com.lumora.cloud.catalog.utils.CatalogInputMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ModelRouteService {

    private final ModelRouteMapper routeMapper;
    private final ModelRouteCostRuleMapper costRuleMapper;
    private final ProviderAccessService providerAccessService;
    private final CatalogInputMapper inputMapper;

    public void createPrimary(ModelVersionEntity version, ProviderEntity provider, ModelVersionValues values) {
        CostTimePricingPolicyValues policy = values.costTimePricingPolicy();
        ModelRouteEntity route = ModelRouteEntity.create(
                version.getId(), provider, "默认路由 · " + provider.getName(), values.upstreamModel(),
                100, 100, null, null, null, true, true, "ACTIVE", true,
                values.costCurrency(), values.inputCostPerMillion(), values.cacheReadCostPerMillion(),
                values.cacheWriteCostPerMillion(), values.outputCostPerMillion(),
                policy == null ? null : policy.zoneId()
        );
        routeMapper.insert(route);
        replaceCostRules(route.getId(), policy);
    }

    public void syncPrimary(ModelVersionEntity version, ProviderEntity provider, ModelVersionValues values) {
        ModelRouteEntity route = routeMapper.findPrimaryForUpdate(version.getId());
        if (route == null) {
            createPrimary(version, provider, values);
            return;
        }
        CostTimePricingPolicyValues policy = values.costTimePricingPolicy();
        route.apply(
                provider, route.getRouteName(), values.upstreamModel(), route.getPriority(), route.getWeight(),
                route.getMaxConcurrency(), route.getRequestsPerMinute(), route.getTokensPerMinute(),
                Boolean.TRUE.equals(route.getFailoverEnabled()), Boolean.TRUE.equals(route.getCircuitBreakerEnabled()),
                route.getStatus(), values.costCurrency(), values.inputCostPerMillion(),
                values.cacheReadCostPerMillion(), values.cacheWriteCostPerMillion(), values.outputCostPerMillion(),
                policy == null ? null : policy.zoneId()
        );
        if (routeMapper.updateOptimistic(route, route.getRevision()) != 1) {
            throw routeConflict();
        }
        replaceCostRules(route.getId(), policy);
    }

    public void copy(String sourceVersionId, String targetVersionId) {
        for (ModelRouteEntity source : routeMapper.findByVersionId(sourceVersionId)) {
            ModelRouteEntity target = ModelRouteEntity.copyTo(targetVersionId, source);
            routeMapper.insert(target);
            for (ModelRouteCostRuleEntity rule : costRuleMapper.findByRouteId(source.getId())) {
                costRuleMapper.insert(ModelRouteCostRuleEntity.copyTo(target.getId(), rule));
            }
        }
    }

    public ModelRouteResponse createSecondary(ModelVersionEntity draft, ModelRouteInput input) {
        ProviderEntity provider = providerAccessService.requireActiveForUpdate(input.providerId());
        validateProtocol(provider, draft.getSupportsWebSearch());
        CostTimePricingPolicyValues policy = inputMapper.costTimePricingPolicy(input.costTimePricingPolicy());
        ModelRouteEntity route = route(input, draft.getId(), provider, false, policy);
        try {
            routeMapper.insert(route);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "MODEL_ROUTE_NAME_EXISTS", "当前草稿中已经存在同名路由");
        }
        replaceCostRules(route.getId(), policy);
        return response(find(route.getId(), draft.getId()));
    }

    public ModelRouteResponse update(ModelVersionEntity draft, String routeId, long expectedRevision, ModelRouteInput input) {
        ModelRouteEntity route = requireForUpdate(routeId, draft.getId());
        if (route.getRevision() != expectedRevision) {
            throw routeConflict();
        }
        boolean primary = Boolean.TRUE.equals(route.getIsPrimary());
        ProviderEntity provider = providerAccessService.requireActiveForUpdate(primary ? route.getProviderId() : input.providerId());
        validateProtocol(provider, draft.getSupportsWebSearch());
        CostTimePricingPolicyValues policy = primary
                ? null
                : inputMapper.costTimePricingPolicy(input.costTimePricingPolicy());
        if (primary) {
            applyPrimaryRoutingPolicy(route, provider, input);
        } else {
            applySecondaryRoute(route, provider, input, policy);
        }
        try {
            if (routeMapper.updateOptimistic(route, expectedRevision) != 1) {
                throw routeConflict();
            }
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "MODEL_ROUTE_NAME_EXISTS", "当前草稿中已经存在同名路由");
        }
        if (!primary) {
            replaceCostRules(routeId, policy);
        }
        return response(find(routeId, draft.getId()));
    }

    private void applyPrimaryRoutingPolicy(ModelRouteEntity route, ProviderEntity provider, ModelRouteInput input) {
        route.apply(
                provider, route.getRouteName(), route.getUpstreamModel(), input.priority(), input.weight(),
                input.maxConcurrency(), input.requestsPerMinute(), input.tokensPerMinute(), input.failoverEnabled(),
                input.circuitBreakerEnabled(), input.status(), route.getCostCurrency(),
                route.getInputCostPerMillion(), route.getCacheReadCostPerMillion(),
                route.getCacheWriteCostPerMillion(), route.getOutputCostPerMillion(),
                Boolean.TRUE.equals(route.getCostTimePricingEnabled()) ? route.getCostTimePricingZone() : null
        );
    }

    private void applySecondaryRoute(
            ModelRouteEntity route,
            ProviderEntity provider,
            ModelRouteInput input,
            CostTimePricingPolicyValues policy
    ) {
        route.apply(
                provider, input.routeName().trim(), input.upstreamModel().trim(), input.priority(), input.weight(),
                input.maxConcurrency(), input.requestsPerMinute(), input.tokensPerMinute(), input.failoverEnabled(),
                input.circuitBreakerEnabled(), input.status(), input.costCurrency().trim().toUpperCase(Locale.ROOT),
                CatalogAmounts.nonNegative(input.uncachedInputCostPerMillion(), "uncachedInputCostPerMillion"),
                CatalogAmounts.nonNegative(input.cachedInputCostPerMillion(), "cachedInputCostPerMillion"),
                CatalogAmounts.optionalNonNegative(input.cacheCreationInputCostPerMillion(), "cacheCreationInputCostPerMillion"),
                CatalogAmounts.nonNegative(input.outputCostPerMillion(), "outputCostPerMillion"),
                policy == null ? null : policy.zoneId()
        );
    }

    public void delete(ModelVersionEntity draft, String routeId, long expectedRevision) {
        ModelRouteEntity route = requireForUpdate(routeId, draft.getId());
        if (Boolean.TRUE.equals(route.getIsPrimary())) {
            throw new ApiException(HttpStatus.CONFLICT, "PRIMARY_MODEL_ROUTE_REQUIRED", "默认路由不能删除，可将其停用或更换供应商");
        }
        if (routeMapper.deleteSecondaryOptimistic(routeId, draft.getId(), expectedRevision) != 1) {
            throw routeConflict();
        }
    }

    public void validateForPublish(ModelVersionEntity draft) {
        List<ModelRouteEntity> routes = routeMapper.findByVersionId(draft.getId());
        long active = routes.stream().filter(route -> "ACTIVE".equals(route.getStatus())).count();
        if (active == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_MODEL_ROUTE_REQUIRED", "发布前至少需要一条可用上游路由");
        }
        for (ModelRouteEntity route : routes) {
            if (!"ACTIVE".equals(route.getStatus())) {
                continue;
            }
            ProviderEntity provider = providerAccessService.requireActiveForUpdate(route.getProviderId());
            if (route.getActiveCredentialReference() == null) {
                throw new ApiException(HttpStatus.CONFLICT, "MODEL_ROUTE_PROVIDER_DISABLED",
                        "路由“" + route.getRouteName() + "”引用的供应商账号不可用");
            }
            if (!route.getProtocolType().equals(provider.getProtocolType())
                    || !route.getBaseUrl().equals(provider.getBaseUrl())) {
                throw new ApiException(HttpStatus.CONFLICT, "MODEL_ROUTE_PROVIDER_STALE",
                        "路由“" + route.getRouteName() + "”的供应商协议或 API 地址已变更，请重新保存路由");
            }
            validateProtocol(route.getProtocolType(), draft.getSupportsWebSearch());
        }
    }

    public List<ModelRouteResponse> adminRoutes(String versionId) {
        return routeMapper.findByVersionId(versionId).stream().map(this::response).toList();
    }

    public List<CatalogContracts.ResolvedModelRoute> resolvedRoutes(String versionId) {
        return routeMapper.findByVersionId(versionId).stream()
                .filter(route -> "ACTIVE".equals(route.getStatus()) && "ACTIVE".equals(route.getProviderStatus())
                        && route.getActiveCredentialReference() != null)
                .map(this::resolved)
                .toList();
    }

    private ModelRouteEntity route(
            ModelRouteInput input, String versionId, ProviderEntity provider, boolean primary,
            CostTimePricingPolicyValues policy
    ) {
        return ModelRouteEntity.create(
                versionId, provider, input.routeName().trim(), input.upstreamModel().trim(),
                input.priority(), input.weight(), input.maxConcurrency(), input.requestsPerMinute(),
                input.tokensPerMinute(), input.failoverEnabled(), input.circuitBreakerEnabled(), input.status(),
                primary, input.costCurrency().trim().toUpperCase(Locale.ROOT),
                CatalogAmounts.nonNegative(input.uncachedInputCostPerMillion(), "uncachedInputCostPerMillion"),
                CatalogAmounts.nonNegative(input.cachedInputCostPerMillion(), "cachedInputCostPerMillion"),
                CatalogAmounts.optionalNonNegative(input.cacheCreationInputCostPerMillion(), "cacheCreationInputCostPerMillion"),
                CatalogAmounts.nonNegative(input.outputCostPerMillion(), "outputCostPerMillion"),
                policy == null ? null : policy.zoneId()
        );
    }

    private void replaceCostRules(String routeId, CostTimePricingPolicyValues policy) {
        costRuleMapper.deleteByRouteId(routeId);
        if (policy == null) {
            return;
        }
        for (int index = 0; index < policy.rules().size(); index++) {
            costRuleMapper.insert(ModelRouteCostRuleEntity.create(routeId, index, policy.rules().get(index)));
        }
    }

    private ModelRouteEntity requireForUpdate(String routeId, String versionId) {
        ModelRouteEntity route = routeMapper.findForUpdate(routeId, versionId);
        if (route == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MODEL_ROUTE_NOT_FOUND", "模型上游路由不存在");
        }
        return route;
    }

    private ModelRouteEntity find(String routeId, String versionId) {
        return routeMapper.findByVersionId(versionId).stream()
                .filter(route -> routeId.equals(route.getId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MODEL_ROUTE_NOT_FOUND", "模型上游路由不存在"));
    }

    private ModelRouteResponse response(ModelRouteEntity route) {
        return new ModelRouteResponse(
                route.getId(), route.getRouteName(), route.getProviderId(), route.getProviderCode(),
                route.getProviderName(), route.getProtocolType(), route.getBaseUrl(), route.getUpstreamModel(),
                route.getPriority(), route.getWeight(), route.getMaxConcurrency(), route.getRequestsPerMinute(),
                route.getTokensPerMinute(), route.getAccountMaxConcurrency(), route.getAccountRequestsPerMinute(),
                route.getAccountTokensPerMinute(), Boolean.TRUE.equals(route.getFailoverEnabled()),
                Boolean.TRUE.equals(route.getCircuitBreakerEnabled()), route.getStatus(),
                Boolean.TRUE.equals(route.getIsPrimary()), route.getCostCurrency(),
                new CostRates(
                        route.getInputCostPerMillion(), route.getCacheReadCostPerMillion(),
                        route.getCacheWriteCostPerMillion(), route.getOutputCostPerMillion()
                ), adminCostPolicy(route), route.getRevision(), route.getCreatedAt(), route.getUpdatedAt()
        );
    }

    private CatalogContracts.ResolvedModelRoute resolved(ModelRouteEntity route) {
        return new CatalogContracts.ResolvedModelRoute(
                route.getId(), route.getRouteName(), route.getProviderId(), route.getProviderCode(),
                route.getProtocolType(), route.getBaseUrl(), route.getActiveCredentialReference(),
                route.getUpstreamModel(), route.getPriority(), route.getWeight(), route.getMaxConcurrency(),
                route.getRequestsPerMinute(), route.getTokensPerMinute(), route.getAccountMaxConcurrency(),
                route.getAccountRequestsPerMinute(), route.getAccountTokensPerMinute(),
                Boolean.TRUE.equals(route.getFailoverEnabled()), Boolean.TRUE.equals(route.getCircuitBreakerEnabled()),
                route.getCostCurrency(), new CatalogContracts.CostRates(
                        route.getInputCostPerMillion(), route.getCacheReadCostPerMillion(),
                        route.getCacheWriteCostPerMillion(), route.getOutputCostPerMillion()
                ), resolvedCostPolicy(route)
        );
    }

    private CostTimePricingPolicy adminCostPolicy(ModelRouteEntity route) {
        if (!Boolean.TRUE.equals(route.getCostTimePricingEnabled())) {
            return null;
        }
        return new CostTimePricingPolicy(
                route.getCostTimePricingZone(), costRuleMapper.findByRouteId(route.getId()).stream()
                .map(rule -> new CostTimePricingRule(
                        rule.getName(), days(rule.getDaysMask()), rule.getStartTime(), rule.getEndTime(),
                        new CostRates(
                                rule.getInputCostPerMillion(), rule.getCacheReadCostPerMillion(),
                                rule.getCacheWriteCostPerMillion(), rule.getOutputCostPerMillion()
                        )
                )).toList()
        );
    }

    private CatalogContracts.CostTimePricingPolicy resolvedCostPolicy(ModelRouteEntity route) {
        CostTimePricingPolicy policy = adminCostPolicy(route);
        if (policy == null) {
            return null;
        }
        return new CatalogContracts.CostTimePricingPolicy(
                policy.zoneId(), policy.rules().stream().map(rule -> new CatalogContracts.CostTimePricingRule(
                        rule.name(), rule.daysOfWeek(), rule.startTime(), rule.endTime(),
                        new CatalogContracts.CostRates(
                                rule.costRates().uncachedInputPerMillion(), rule.costRates().cachedInputPerMillion(),
                                rule.costRates().cacheCreationInputPerMillion(), rule.costRates().outputPerMillion()
                        )
                )).toList()
        );
    }

    private List<DayOfWeek> days(int mask) {
        List<DayOfWeek> result = new ArrayList<>(7);
        for (DayOfWeek day : DayOfWeek.values()) {
            if ((mask & (1 << (day.getValue() - 1))) != 0) {
                result.add(day);
            }
        }
        return List.copyOf(result);
    }

    private void validateProtocol(ProviderEntity provider, boolean webSearch) {
        validateProtocol(provider.getProtocolType(), webSearch);
    }

    private void validateProtocol(String protocol, boolean webSearch) {
        if (webSearch && !"ANTHROPIC".equals(protocol) && !"RESPONSES".equals(protocol)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HOSTED_WEB_SEARCH_PROTOCOL_UNSUPPORTED",
                    "启用托管 Web Search 的模型路由仅支持 Anthropic Messages 或 Responses 协议");
        }
    }

    private ApiException routeConflict() {
        return new ApiException(HttpStatus.CONFLICT, "MODEL_ROUTE_REVISION_CONFLICT", "模型路由已被其他操作更新，请刷新后重试");
    }
}
