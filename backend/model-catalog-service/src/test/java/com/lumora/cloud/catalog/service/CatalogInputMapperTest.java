package com.lumora.cloud.catalog.service;

import com.lumora.cloud.catalog.domain.ModelVersionValues;
import com.lumora.cloud.catalog.error.ApiException;
import com.lumora.cloud.catalog.web.CatalogWebContracts.CostTimePricingPolicyInput;
import com.lumora.cloud.catalog.web.CatalogWebContracts.CostTimePricingRuleInput;
import com.lumora.cloud.catalog.web.CatalogWebContracts.CostRateInput;
import com.lumora.cloud.catalog.web.CatalogWebContracts.ModelVersionInput;
import com.lumora.cloud.catalog.web.CatalogWebContracts.QuotaTimePricingPolicyInput;
import com.lumora.cloud.catalog.web.CatalogWebContracts.QuotaTimePricingRuleInput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogInputMapperTest {

    private final CatalogInputMapper mapper = new CatalogInputMapper();

    @Test
    void normalizesEverySupportedProviderProtocol() {
        assertThat(mapper.protocolType(" anthropic ")).isEqualTo("ANTHROPIC");
        assertThat(mapper.protocolType("openai_compatible")).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(mapper.protocolType("responses")).isEqualTo("RESPONSES");
    }

    @Test
    void rejectsUnknownProviderProtocol() {
        assertThatThrownBy(() -> mapper.protocolType("custom"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("UNSUPPORTED_PROVIDER_PROTOCOL");
    }

    @Test
    void acceptsIndependentOptionalCostAndQuotaTimePolicies() {
        var values = mapper.values(version(
                costPolicy(costRule("供应商峰时", List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
                        LocalTime.of(9, 0), LocalTime.of(12, 0))),
                quotaPolicy(quotaRule("套餐夜间", List.of(DayOfWeek.FRIDAY),
                        LocalTime.of(22, 0), LocalTime.of(6, 0), "0.8"))
        ));

        assertThat(values.cacheWriteCostPerMillion()).isEqualByComparingTo("0.000000");
        assertThat(values.costTimePricingPolicy().rules()).hasSize(1);
        assertThat(values.costTimePricingPolicy().rules().getFirst().costRates().cacheWritePerMillion())
                .isEqualByComparingTo("0.000000");
        assertThat(values.quotaTimePricingPolicy().rules()).hasSize(1);
        assertThat(values.quotaTimePricingPolicy().rules().getFirst().startTime())
                .isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    void rejectsInvalidTimePricingZone() {
        var invalid = new CostTimePricingPolicyInput(
                "Not/A_Time_Zone",
                List.of(costRule("峰时", List.of(DayOfWeek.MONDAY),
                        LocalTime.of(9, 0), LocalTime.of(18, 0)))
        );
        assertThatThrownBy(() -> mapper.values(version(invalid, null)))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("INVALID_COST_TIME_ZONE");
    }

    @Test
    void rejectsOverlappingRulesIncludingOvernightTail() {
        assertThatThrownBy(() -> mapper.values(version(null, quotaPolicy(
                quotaRule("周五夜间", List.of(DayOfWeek.FRIDAY),
                        LocalTime.of(22, 0), LocalTime.of(6, 0), "1.2"),
                quotaRule("周六清晨", List.of(DayOfWeek.SATURDAY),
                        LocalTime.of(5, 0), LocalTime.of(8, 0), "0.8")
        ))))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("OVERLAPPING_TIME_PRICING_RULES");
    }

    @Test
    void acceptsMultipleNonOverlappingCostRangesOnTheSameWeekdays() {
        List<DayOfWeek> weekdays = weekdays();

        var values = mapper.values(version(costPolicy(
                costRule("工作日峰时 1", weekdays, LocalTime.of(9, 0), LocalTime.of(12, 0)),
                costRule("工作日峰时 2", weekdays, LocalTime.of(14, 0), LocalTime.of(18, 0))
        ), null));

        assertThat(values.costTimePricingPolicy().rules())
                .extracting(ModelVersionValues.CostTimePricingRuleValues::name)
                .containsExactly("工作日峰时 1", "工作日峰时 2");
    }

    @Test
    void acceptsMultipleNonOverlappingQuotaRangesOnTheSameWeekdays() {
        List<DayOfWeek> weekdays = weekdays();

        var values = mapper.values(version(null, quotaPolicy(
                quotaRule("工作日额度峰时 1", weekdays, LocalTime.of(9, 0), LocalTime.of(12, 0), "1.2"),
                quotaRule("工作日额度峰时 2", weekdays, LocalTime.of(14, 0), LocalTime.of(18, 0), "1.2")
        )));

        assertThat(values.quotaTimePricingPolicy().rules())
                .extracting(ModelVersionValues.QuotaTimePricingRuleValues::name)
                .containsExactly("工作日额度峰时 1", "工作日额度峰时 2");
    }

    private ModelVersionInput version(
            CostTimePricingPolicyInput costPolicy,
            QuotaTimePricingPolicyInput quotaPolicy
    ) {
        BigDecimal zero = BigDecimal.ZERO;
        BigDecimal one = BigDecimal.ONE;
        return new ModelVersionInput(
                "Test", null, "upstream", 8_192, 1_024,
                true, true, false, true, false, "CNY",
                zero, zero, null, zero, costPolicy,
                one, zero, null, one, zero, quotaPolicy
        );
    }

    private CostTimePricingPolicyInput costPolicy(CostTimePricingRuleInput... rules) {
        return new CostTimePricingPolicyInput("Asia/Shanghai", List.of(rules));
    }

    private QuotaTimePricingPolicyInput quotaPolicy(QuotaTimePricingRuleInput... rules) {
        return new QuotaTimePricingPolicyInput("Asia/Shanghai", BigDecimal.ONE, List.of(rules));
    }

    private CostTimePricingRuleInput costRule(
            String name,
            List<DayOfWeek> days,
            LocalTime start,
            LocalTime end
    ) {
        return new CostTimePricingRuleInput(
                name, days, start, end, rates("0.10", "0.02", null, "0.30")
        );
    }

    private QuotaTimePricingRuleInput quotaRule(
            String name,
            List<DayOfWeek> days,
            LocalTime start,
            LocalTime end,
            String multiplier
    ) {
        return new QuotaTimePricingRuleInput(name, days, start, end, new BigDecimal(multiplier));
    }

    private CostRateInput rates(String input, String cached, String creation, String output) {
        return new CostRateInput(
                new BigDecimal(input), new BigDecimal(cached),
                creation == null ? null : new BigDecimal(creation), new BigDecimal(output)
        );
    }

    private List<DayOfWeek> weekdays() {
        return List.of(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
        );
    }
}
