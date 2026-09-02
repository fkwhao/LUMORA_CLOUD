package com.lumora.cloud.catalog.service;

import com.lumora.cloud.catalog.domain.ModelVersionValues;
import com.lumora.cloud.catalog.domain.ModelVersionValues.CostTimePricingPolicyValues;
import com.lumora.cloud.catalog.domain.ModelVersionValues.CostTimePricingRuleValues;
import com.lumora.cloud.catalog.domain.ModelVersionValues.CostRatesValues;
import com.lumora.cloud.catalog.domain.ModelVersionValues.QuotaTimePricingPolicyValues;
import com.lumora.cloud.catalog.domain.ModelVersionValues.QuotaTimePricingRuleValues;
import com.lumora.cloud.catalog.error.ApiException;
import com.lumora.cloud.catalog.web.CatalogWebContracts.CostRateInput;
import com.lumora.cloud.catalog.web.CatalogWebContracts.CostTimePricingPolicyInput;
import com.lumora.cloud.catalog.web.CatalogWebContracts.CostTimePricingRuleInput;
import com.lumora.cloud.catalog.web.CatalogWebContracts.ModelVersionInput;
import com.lumora.cloud.catalog.web.CatalogWebContracts.QuotaTimePricingPolicyInput;
import com.lumora.cloud.catalog.web.CatalogWebContracts.QuotaTimePricingRuleInput;
import com.lumora.cloud.api.catalog.ProviderProtocol;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;

@Component
public class CatalogInputMapper {

    private static final long DAY_NANOS = java.time.Duration.ofDays(1).toNanos();

    public ModelVersionValues values(ModelVersionInput input) {
        if (input.maxOutputTokens() > input.contextWindow()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MODEL_LIMITS",
                    "最大输出 Token 不能超过上下文窗口");
        }
        ModelVersionValues values = new ModelVersionValues(
                input.displayName().trim(), trimToNull(input.description()), input.upstreamModel().trim(),
                input.contextWindow(), input.maxOutputTokens(), input.supportsReasoning(), input.supportsTools(),
                input.supportsVision(), input.supportsJson(), input.supportsWebSearch(),
                input.costCurrency().trim().toUpperCase(Locale.ROOT),
                CatalogAmounts.nonNegative(input.uncachedInputCostPerMillion(), "uncachedInputCostPerMillion"),
                CatalogAmounts.nonNegative(input.outputCostPerMillion(), "outputCostPerMillion"),
                CatalogAmounts.nonNegative(input.cachedInputCostPerMillion(), "cachedInputCostPerMillion"),
                CatalogAmounts.optionalNonNegative(input.cacheCreationInputCostPerMillion(),
                        "cacheCreationInputCostPerMillion"),
                costTimePricingPolicy(input.costTimePricingPolicy()),
                CatalogAmounts.nonNegative(input.uncachedInputQuotaPerMillion(), "uncachedInputQuotaPerMillion"),
                CatalogAmounts.nonNegative(input.outputQuotaPerMillion(), "outputQuotaPerMillion"),
                CatalogAmounts.nonNegative(input.cachedInputQuotaPerMillion(), "cachedInputQuotaPerMillion"),
                CatalogAmounts.optionalNonNegative(input.cacheCreationInputQuotaPerMillion(),
                        "cacheCreationInputQuotaPerMillion"),
                CatalogAmounts.nonNegative(input.minimumRequestQuota(), "minimumRequestQuota"),
                quotaTimePricingPolicy(input.quotaTimePricingPolicy())
        );
        if (values.inputQuotaPerMillion().signum() == 0
                && values.outputQuotaPerMillion().signum() == 0
                && values.cacheReadQuotaPerMillion().signum() == 0
                && values.cacheWriteQuotaPerMillion().signum() == 0
                && values.minimumRequestQuota().signum() == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MODEL_QUOTA_RATES_REQUIRED",
                    "云端模型至少需要配置一个正数额度费率或最低请求额度");
        }
        return values;
    }

    private CostTimePricingPolicyValues costTimePricingPolicy(CostTimePricingPolicyInput input) {
        if (input == null) {
            return null;
        }
        String zoneId = zoneId(input.zoneId(), "INVALID_COST_TIME_ZONE", "上游成本");
        EnumMap<DayOfWeek, ArrayList<DailyInterval>> occupied = new EnumMap<>(DayOfWeek.class);
        ArrayList<CostTimePricingRuleValues> rules = new ArrayList<>(input.rules().size());
        for (int index = 0; index < input.rules().size(); index++) {
            CostTimePricingRuleInput rule = input.rules().get(index);
            validateRange(rule.name(), rule.startTime(), rule.endTime());
            int daysMask = daysMask(rule.name(), rule.daysOfWeek());
            rejectOverlap(rule.name(), rule.daysOfWeek(), rule.startTime(), rule.endTime(), occupied);
            rules.add(new CostTimePricingRuleValues(
                    rule.name().trim(), daysMask, rule.startTime(), rule.endTime(),
                    costRates(rule.costRates(), "costTimePricingPolicy.rules[" + index + "].costRates")
            ));
        }
        return new CostTimePricingPolicyValues(zoneId, rules);
    }

    private QuotaTimePricingPolicyValues quotaTimePricingPolicy(QuotaTimePricingPolicyInput input) {
        if (input == null) {
            return null;
        }
        String zoneId = zoneId(input.zoneId(), "INVALID_QUOTA_TIME_ZONE", "套餐额度");
        EnumMap<DayOfWeek, ArrayList<DailyInterval>> occupied = new EnumMap<>(DayOfWeek.class);
        ArrayList<QuotaTimePricingRuleValues> rules = new ArrayList<>(input.rules().size());
        for (int index = 0; index < input.rules().size(); index++) {
            QuotaTimePricingRuleInput rule = input.rules().get(index);
            validateRange(rule.name(), rule.startTime(), rule.endTime());
            int daysMask = daysMask(rule.name(), rule.daysOfWeek());
            rejectOverlap(rule.name(), rule.daysOfWeek(), rule.startTime(), rule.endTime(), occupied);
            rules.add(new QuotaTimePricingRuleValues(
                    rule.name().trim(), daysMask, rule.startTime(), rule.endTime(),
                    CatalogAmounts.positive(rule.quotaMultiplier(),
                            "quotaTimePricingPolicy.rules[" + index + "].quotaMultiplier")
            ));
        }
        return new QuotaTimePricingPolicyValues(
                zoneId,
                CatalogAmounts.positive(input.defaultQuotaMultiplier(),
                        "quotaTimePricingPolicy.defaultQuotaMultiplier"),
                rules
        );
    }

    private String zoneId(String value, String code, String scope) {
        try {
            return ZoneId.of(value.trim()).getId();
        } catch (DateTimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code,
                    scope + "时段计价时区必须是有效的 IANA 时区，例如 Asia/Shanghai");
        }
    }

    private void validateRange(String name, java.time.LocalTime startTime, java.time.LocalTime endTime) {
        if (startTime.equals(endTime)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIME_PRICING_RANGE",
                    "时段规则“" + name.trim() + "”的开始时间和结束时间不能相同");
        }
    }

    private int daysMask(String ruleName, java.util.List<DayOfWeek> daysOfWeek) {
        int mask = 0;
        for (DayOfWeek day : daysOfWeek) {
            int bit = 1 << (day.getValue() - 1);
            if ((mask & bit) != 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_TIME_PRICING_DAY",
                        "时段规则“" + ruleName.trim() + "”包含重复星期");
            }
            mask |= bit;
        }
        return mask;
    }

    private void rejectOverlap(
            String ruleName,
            java.util.List<DayOfWeek> daysOfWeek,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime,
            EnumMap<DayOfWeek, ArrayList<DailyInterval>> occupied
    ) {
        for (DayOfWeek day : daysOfWeek) {
            if (startTime.isBefore(endTime)) {
                addInterval(ruleName, day, startTime.toNanoOfDay(), endTime.toNanoOfDay(), occupied);
            } else {
                addInterval(ruleName, day, startTime.toNanoOfDay(), DAY_NANOS, occupied);
                if (!endTime.equals(java.time.LocalTime.MIDNIGHT)) {
                    addInterval(ruleName, day.plus(1), 0, endTime.toNanoOfDay(), occupied);
                }
            }
        }
    }

    private void addInterval(
            String ruleName,
            DayOfWeek day,
            long start,
            long end,
            EnumMap<DayOfWeek, ArrayList<DailyInterval>> occupied
    ) {
        ArrayList<DailyInterval> intervals = occupied.computeIfAbsent(day, ignored -> new ArrayList<>());
        DailyInterval conflict = intervals.stream()
                .filter(existing -> start < existing.end() && existing.start() < end)
                .findFirst()
                .orElse(null);
        if (conflict != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OVERLAPPING_TIME_PRICING_RULES",
                    "时段规则“" + ruleName.trim() + "”与“" + conflict.ruleName()
                            + "”在" + dayLabel(day) + "存在时间重叠，请调整时间");
        }
        intervals.add(new DailyInterval(start, end, ruleName.trim()));
    }

    private String dayLabel(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }

    private CostRatesValues costRates(CostRateInput input, String field) {
        return new CostRatesValues(
                CatalogAmounts.nonNegative(input.uncachedInputPerMillion(), field + ".uncachedInputPerMillion"),
                CatalogAmounts.nonNegative(input.outputPerMillion(), field + ".outputPerMillion"),
                CatalogAmounts.nonNegative(input.cachedInputPerMillion(), field + ".cachedInputPerMillion"),
                CatalogAmounts.optionalNonNegative(input.cacheCreationInputPerMillion(),
                        field + ".cacheCreationInputPerMillion")
        );
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

    private record DailyInterval(long start, long end, String ruleName) {
    }
}
