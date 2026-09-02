package com.lumora.cloud.catalog.web;

import com.lumora.cloud.api.catalog.CatalogContracts.ModelCapabilities;
import com.lumora.cloud.api.catalog.CatalogContracts.QuotaRates;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public final class CatalogWebContracts {

    private CatalogWebContracts() {
    }

    public record CreateProviderRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,62}[a-z0-9]") String code,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}") String protocolType,
            @NotBlank @Size(max = 500) String baseUrl,
            @NotBlank @Size(max = 8192) String apiKey,
            @Min(1) @Max(1_000_000) Integer maxConcurrency,
            @Min(1) @Max(10_000_000) Integer requestsPerMinute,
            @Min(1) Long tokensPerMinute
    ) {
        public CreateProviderRequest(String code, String name, String protocolType, String baseUrl, String apiKey) {
            this(code, name, protocolType, baseUrl, apiKey, null, null, null);
        }
    }

    public record UpdateProviderRequest(
            @Min(0) long expectedRevision,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}") String protocolType,
            @NotBlank @Size(max = 500) String baseUrl,
            @Min(1) @Max(1_000_000) Integer maxConcurrency,
            @Min(1) @Max(10_000_000) Integer requestsPerMinute,
            @Min(1) Long tokensPerMinute,
            @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status
    ) {
        public UpdateProviderRequest(
                long expectedRevision, String name, String protocolType, String baseUrl, String status
        ) {
            this(expectedRevision, name, protocolType, baseUrl, null, null, null, status);
        }
    }

    public record RotateProviderCredentialRequest(
            @Min(0) long expectedRevision,
            @NotBlank @Size(max = 8192) String apiKey
    ) {
    }

    public record ProviderCredentialStatusResponse(
            String storageType,
            boolean managed,
            String maskedValue,
            String fingerprint,
            long revision,
            Instant rotatedAt
    ) {
    }

    public record ProviderResponse(
            Long id,
            String code,
            String name,
            String protocolType,
            String baseUrl,
            Integer maxConcurrency,
            Integer requestsPerMinute,
            Long tokensPerMinute,
            ProviderCredentialStatusResponse credential,
            String status,
            long revision,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ModelVersionInput(
            @NotBlank @Size(max = 120) String displayName,
            @Size(max = 500) String description,
            @NotBlank @Size(max = 160) String upstreamModel,
            @Min(1) long contextWindow,
            @Min(1) long maxOutputTokens,
            boolean supportsReasoning,
            boolean supportsTools,
            boolean supportsVision,
            boolean supportsJson,
            boolean supportsWebSearch,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String costCurrency,
            @NotNull @DecimalMin("0") BigDecimal uncachedInputCostPerMillion,
            @NotNull @DecimalMin("0") BigDecimal cachedInputCostPerMillion,
            @DecimalMin("0") BigDecimal cacheCreationInputCostPerMillion,
            @NotNull @DecimalMin("0") BigDecimal outputCostPerMillion,
            @Valid CostTimePricingPolicyInput costTimePricingPolicy,
            @NotNull @DecimalMin("0") BigDecimal uncachedInputQuotaPerMillion,
            @NotNull @DecimalMin("0") BigDecimal cachedInputQuotaPerMillion,
            @DecimalMin("0") BigDecimal cacheCreationInputQuotaPerMillion,
            @NotNull @DecimalMin("0") BigDecimal outputQuotaPerMillion,
            @NotNull @DecimalMin("0") BigDecimal minimumRequestQuota,
            @Valid QuotaTimePricingPolicyInput quotaTimePricingPolicy
    ) {
    }

    public record CostTimePricingPolicyInput(
            @NotBlank @Size(max = 64) String zoneId,
            @NotNull @Size(min = 1, max = 32) List<@Valid CostTimePricingRuleInput> rules
    ) {
        public CostTimePricingPolicyInput {
            rules = rules == null ? null : List.copyOf(rules);
        }
    }

    public record CostTimePricingRuleInput(
            @NotBlank @Size(max = 80) String name,
            @NotNull @Size(min = 1, max = 7) List<@NotNull DayOfWeek> daysOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull @Valid CostRateInput costRates
    ) {
        public CostTimePricingRuleInput {
            daysOfWeek = daysOfWeek == null ? null : List.copyOf(daysOfWeek);
        }
    }

    public record QuotaTimePricingPolicyInput(
            @NotBlank @Size(max = 64) String zoneId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal defaultQuotaMultiplier,
            @NotNull @Size(min = 1, max = 32) List<@Valid QuotaTimePricingRuleInput> rules
    ) {
        public QuotaTimePricingPolicyInput {
            rules = rules == null ? null : List.copyOf(rules);
        }
    }

    public record QuotaTimePricingRuleInput(
            @NotBlank @Size(max = 80) String name,
            @NotNull @Size(min = 1, max = 7) List<@NotNull DayOfWeek> daysOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quotaMultiplier
    ) {
        public QuotaTimePricingRuleInput {
            daysOfWeek = daysOfWeek == null ? null : List.copyOf(daysOfWeek);
        }
    }

    public record CostRateInput(
            @NotNull @DecimalMin("0") BigDecimal uncachedInputPerMillion,
            @NotNull @DecimalMin("0") BigDecimal cachedInputPerMillion,
            @DecimalMin("0") BigDecimal cacheCreationInputPerMillion,
            @NotNull @DecimalMin("0") BigDecimal outputPerMillion
    ) {
    }

    public record CreateModelRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9._-]{1,126}[a-z0-9]") String code,
            @NotNull @Min(1) Long providerId,
            @NotNull @Valid ModelVersionInput version
    ) {
    }

    public record UpdateDraftRequest(
            @Min(0) long expectedRevision,
            @NotNull @Min(1) Long providerId,
            @NotNull @Valid ModelVersionInput version
    ) {
    }

    public record PublishDraftRequest(@Min(0) long expectedRevision) {
    }

    public record UpdateModelStatusRequest(
            @Min(0) long expectedRevision,
            @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status
    ) {
    }

    public record ModelVersionResponse(
            String id,
            String pricingVersion,
            int versionNo,
            String status,
            long revision,
            Long providerId,
            String displayName,
            String description,
            String upstreamModel,
            String protocolType,
            String baseUrl,
            String credentialReference,
            ModelCapabilities capabilities,
            String costCurrency,
            CostRates costRates,
            CostTimePricingPolicy costTimePricingPolicy,
            QuotaRates quotaRates,
            QuotaTimePricingPolicy quotaTimePricingPolicy,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt,
            List<ModelRouteResponse> routes
    ) {
        public ModelVersionResponse {
            routes = routes == null ? List.of() : List.copyOf(routes);
        }
    }

    public record ModelRouteInput(
            @NotBlank @Size(max = 120) String routeName,
            @NotNull @Min(1) Long providerId,
            @NotBlank @Size(max = 160) String upstreamModel,
            @Min(0) @Max(10_000) int priority,
            @Min(1) @Max(10_000) int weight,
            @Min(1) @Max(1_000_000) Integer maxConcurrency,
            @Min(1) @Max(10_000_000) Integer requestsPerMinute,
            @Min(1) Long tokensPerMinute,
            boolean failoverEnabled,
            boolean circuitBreakerEnabled,
            @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String costCurrency,
            @NotNull @DecimalMin("0") BigDecimal uncachedInputCostPerMillion,
            @NotNull @DecimalMin("0") BigDecimal cachedInputCostPerMillion,
            @DecimalMin("0") BigDecimal cacheCreationInputCostPerMillion,
            @NotNull @DecimalMin("0") BigDecimal outputCostPerMillion,
            @Valid CostTimePricingPolicyInput costTimePricingPolicy
    ) {
    }

    public record CreateModelRouteRequest(@NotNull @Valid ModelRouteInput route) {
    }

    public record UpdateModelRouteRequest(
            @Min(0) long expectedRevision,
            @NotNull @Valid ModelRouteInput route
    ) {
    }

    public record ModelRouteResponse(
            String id,
            String routeName,
            Long providerId,
            String providerCode,
            String providerName,
            String protocolType,
            String baseUrl,
            String upstreamModel,
            int priority,
            int weight,
            Integer maxConcurrency,
            Integer requestsPerMinute,
            Long tokensPerMinute,
            Integer accountMaxConcurrency,
            Integer accountRequestsPerMinute,
            Long accountTokensPerMinute,
            boolean failoverEnabled,
            boolean circuitBreakerEnabled,
            String status,
            boolean primary,
            String costCurrency,
            CostRates costRates,
            CostTimePricingPolicy costTimePricingPolicy,
            long revision,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CostRates(
            BigDecimal uncachedInputPerMillion,
            BigDecimal cachedInputPerMillion,
            BigDecimal cacheCreationInputPerMillion,
            BigDecimal outputPerMillion
    ) {
    }

    public record CostTimePricingPolicy(
            String zoneId,
            List<CostTimePricingRule> rules
    ) {
        public CostTimePricingPolicy {
            rules = List.copyOf(rules);
        }
    }

    public record CostTimePricingRule(
            String name,
            List<DayOfWeek> daysOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            CostRates costRates
    ) {
        public CostTimePricingRule {
            daysOfWeek = List.copyOf(daysOfWeek);
        }
    }

    public record QuotaTimePricingPolicy(
            String zoneId,
            BigDecimal defaultQuotaMultiplier,
            List<QuotaTimePricingRule> rules
    ) {
        public QuotaTimePricingPolicy {
            rules = List.copyOf(rules);
        }
    }

    public record QuotaTimePricingRule(
            String name,
            List<DayOfWeek> daysOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            BigDecimal quotaMultiplier
    ) {
        public QuotaTimePricingRule {
            daysOfWeek = List.copyOf(daysOfWeek);
        }
    }

    public record AdminModelResponse(
            Long modelId,
            String code,
            String status,
            long revision,
            ModelVersionResponse draft,
            ModelVersionResponse published,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PublicModelResponse(
            String code,
            String displayName,
            String description,
            String pricingVersion,
            String providerCode,
            ModelCapabilities capabilities,
            QuotaRates quotaRates,
            QuotaTimePricingPolicy quotaTimePricingPolicy,
            Instant publishedAt
    ) {
    }

    public record AdminCatalogStatisticsResponse(
            long totalProviders,
            long activeProviders,
            long totalModels,
            long publicModels,
            long draftModels,
            long totalVersions,
            Instant generatedAt
    ) {
    }
}
