package com.lumora.cloud.catalog.web;

import com.lumora.cloud.api.catalog.CatalogContracts.ModelCapabilities;
import com.lumora.cloud.api.catalog.CatalogContracts.QuotaRates;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class CatalogWebContracts {

    private CatalogWebContracts() {
    }

    public record CreateProviderRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,62}[a-z0-9]") String code,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}") String protocolType,
            @NotBlank @Size(max = 500) String baseUrl,
            @NotBlank @Size(max = 8192) String apiKey
    ) {
    }

    public record UpdateProviderRequest(
            @Min(0) long expectedRevision,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}") String protocolType,
            @NotBlank @Size(max = 500) String baseUrl,
            @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status
    ) {
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
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String costCurrency,
            @NotNull @DecimalMin("0") BigDecimal inputCostPerMillion,
            @NotNull @DecimalMin("0") BigDecimal outputCostPerMillion,
            @NotNull @DecimalMin("0") BigDecimal reasoningCostPerMillion,
            @NotNull @DecimalMin("0") BigDecimal cacheReadCostPerMillion,
            @NotNull @DecimalMin("0") BigDecimal cacheWriteCostPerMillion,
            @NotNull @DecimalMin("0") BigDecimal inputQuotaPerMillion,
            @NotNull @DecimalMin("0") BigDecimal outputQuotaPerMillion,
            @NotNull @DecimalMin("0") BigDecimal reasoningQuotaPerMillion,
            @NotNull @DecimalMin("0") BigDecimal cacheReadQuotaPerMillion,
            @NotNull @DecimalMin("0") BigDecimal cacheWriteQuotaPerMillion,
            @NotNull @DecimalMin("0") BigDecimal minimumRequestQuota
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
            QuotaRates quotaRates,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CostRates(
            BigDecimal inputPerMillion,
            BigDecimal outputPerMillion,
            BigDecimal reasoningPerMillion,
            BigDecimal cacheReadPerMillion,
            BigDecimal cacheWritePerMillion
    ) {
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
