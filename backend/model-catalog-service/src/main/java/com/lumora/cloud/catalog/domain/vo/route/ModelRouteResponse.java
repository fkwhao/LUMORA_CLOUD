package com.lumora.cloud.catalog.domain.vo.route;

import com.lumora.cloud.catalog.domain.vo.pricing.CostRates;
import com.lumora.cloud.catalog.domain.vo.pricing.CostTimePricingPolicy;

import java.time.Instant;

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
