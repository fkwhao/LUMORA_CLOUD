package com.lumora.cloud.catalog.service;

import com.lumora.cloud.catalog.domain.ModelVersionValues;
import com.lumora.cloud.catalog.error.ApiException;
import com.lumora.cloud.catalog.web.CatalogWebContracts.ModelVersionInput;
import com.lumora.cloud.api.catalog.ProviderProtocol;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Component
public class CatalogInputMapper {

    public ModelVersionValues values(ModelVersionInput input) {
        if (input.maxOutputTokens() > input.contextWindow()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MODEL_LIMITS",
                    "最大输出 Token 不能超过上下文窗口");
        }
        ModelVersionValues values = new ModelVersionValues(
                input.displayName().trim(), trimToNull(input.description()), input.upstreamModel().trim(),
                input.contextWindow(), input.maxOutputTokens(), input.supportsReasoning(), input.supportsTools(),
                input.supportsVision(), input.supportsJson(), input.costCurrency().trim().toUpperCase(Locale.ROOT),
                CatalogAmounts.nonNegative(input.inputCostPerMillion(), "inputCostPerMillion"),
                CatalogAmounts.nonNegative(input.outputCostPerMillion(), "outputCostPerMillion"),
                CatalogAmounts.nonNegative(input.reasoningCostPerMillion(), "reasoningCostPerMillion"),
                CatalogAmounts.nonNegative(input.cacheReadCostPerMillion(), "cacheReadCostPerMillion"),
                CatalogAmounts.nonNegative(input.cacheWriteCostPerMillion(), "cacheWriteCostPerMillion"),
                CatalogAmounts.nonNegative(input.inputQuotaPerMillion(), "inputQuotaPerMillion"),
                CatalogAmounts.nonNegative(input.outputQuotaPerMillion(), "outputQuotaPerMillion"),
                CatalogAmounts.nonNegative(input.reasoningQuotaPerMillion(), "reasoningQuotaPerMillion"),
                CatalogAmounts.nonNegative(input.cacheReadQuotaPerMillion(), "cacheReadQuotaPerMillion"),
                CatalogAmounts.nonNegative(input.cacheWriteQuotaPerMillion(), "cacheWriteQuotaPerMillion"),
                CatalogAmounts.nonNegative(input.minimumRequestQuota(), "minimumRequestQuota")
        );
        if (values.inputQuotaPerMillion().signum() == 0
                && values.outputQuotaPerMillion().signum() == 0
                && values.reasoningQuotaPerMillion().signum() == 0
                && values.cacheReadQuotaPerMillion().signum() == 0
                && values.cacheWriteQuotaPerMillion().signum() == 0
                && values.minimumRequestQuota().signum() == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MODEL_QUOTA_RATES_REQUIRED",
                    "云端模型至少需要配置一个正数额度费率或最低请求额度");
        }
        return values;
    }

    public String baseUrl(String value) {
        String normalized = value.trim();
        try {
            URI uri = new URI(normalized);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null) {
                throw invalidBaseUrl();
            }
        } catch (URISyntaxException exception) {
            throw invalidBaseUrl();
        }
        while (normalized.endsWith("/") && normalized.length() > "https://a".length()) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public String protocolType(String value) {
        try {
            return ProviderProtocol.parse(value).name();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PROVIDER_PROTOCOL",
                    "API 格式仅支持 ANTHROPIC、OPENAI_COMPATIBLE 或 RESPONSES");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ApiException invalidBaseUrl() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROVIDER_BASE_URL",
                "供应商 Base URL 必须是有效的 HTTP/HTTPS 地址，且不能包含查询参数或片段");
    }
}
