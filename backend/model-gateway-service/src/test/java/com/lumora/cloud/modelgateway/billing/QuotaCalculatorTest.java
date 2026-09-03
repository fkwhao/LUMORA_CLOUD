package com.lumora.cloud.modelgateway.billing;

import com.lumora.cloud.api.catalog.CatalogContracts.ModelCapabilities;
import com.lumora.cloud.api.catalog.CatalogContracts.CostRates;
import com.lumora.cloud.api.catalog.CatalogContracts.CostTimePricingPolicy;
import com.lumora.cloud.api.catalog.CatalogContracts.CostTimePricingRule;
import com.lumora.cloud.api.catalog.CatalogContracts.QuotaRates;
import com.lumora.cloud.api.catalog.CatalogContracts.QuotaTimePricingPolicy;
import com.lumora.cloud.api.catalog.CatalogContracts.QuotaTimePricingRule;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.modelgateway.domain.model.TokenUsage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuotaCalculatorTest {

    private final QuotaCalculator calculator = new QuotaCalculator();

    @Test
    void reservesWorstCaseRateButSettlesAuthoritativeSplit() {
        ResolvedModelConfig model = model(new QuotaRates(
                amount("2"), amount("1"), amount("3"), amount("4"), amount("0.000001")
        ));

        var snapshot = calculator.snapshot(model, Instant.now());
        assertThat(calculator.maximum(model, 100, snapshot))
                .isEqualByComparingTo("0.003400");
        assertThat(calculator.actual(model, new TokenUsage(500, 40, 10, 200, 100), snapshot))
                .isEqualByComparingTo("0.001700");
    }

    @Test
    void appliesMinimumRequestQuota() {
        ResolvedModelConfig model = model(new QuotaRates(
                amount("1"), amount("1"), amount("1"), amount("1"), amount("0.25")
        ));

        assertThat(calculator.actual(
                model, new TokenUsage(1, 0, 0, 0, 0), calculator.snapshot(model, Instant.now())
        ))
                .isEqualByComparingTo("0.250000");
    }

    @Test
    void billsReasoningDetailWithTheOutputRate() {
        ResolvedModelConfig model = model(new QuotaRates(
                amount("0"), amount("0"), amount("0"), amount("5"), amount("0")
        ));

        assertThat(calculator.actual(
                model, new TokenUsage(0, 10, 20, 0, 0), calculator.snapshot(model, Instant.now())
        ))
                .isEqualByComparingTo("0.000150");
    }

    @Test
    void matchesSplitWeekdayAndOvernightRulesBeforeApplyingMultiplier() {
        var rates = new QuotaRates(amount("1"), amount("1"), amount("1"), amount("1"), amount("0"));
        var policy = new QuotaTimePricingPolicy("Asia/Shanghai", amount("0.800000"), List.of(
                rule("上午峰时", List.of(DayOfWeek.MONDAY), LocalTime.of(9, 0), LocalTime.of(12, 0), "1.2"),
                rule("下午峰时", List.of(DayOfWeek.MONDAY), LocalTime.of(14, 0), LocalTime.of(18, 0), "1.2"),
                rule("周五夜间", List.of(DayOfWeek.FRIDAY), LocalTime.of(22, 0), LocalTime.of(6, 0), "1.5")
        ));
        ResolvedModelConfig model = model(rates, policy);

        var morning = calculator.snapshot(model, atShanghai(DayOfWeek.MONDAY, 10));
        var lunch = calculator.snapshot(model, atShanghai(DayOfWeek.MONDAY, 13));
        var overnight = calculator.snapshot(model, atShanghai(DayOfWeek.SATURDAY, 1));
        TokenUsage oneMillionInput = new TokenUsage(1_000_000, 0, 0, 0, 0);

        assertThat(morning.ruleName()).isEqualTo("上午峰时");
        assertThat(calculator.actual(model, oneMillionInput, morning)).isEqualByComparingTo("1.200000");
        assertThat(lunch.ruleName()).isNull();
        assertThat(calculator.actual(model, oneMillionInput, lunch)).isEqualByComparingTo("0.800000");
        assertThat(overnight.ruleName()).isEqualTo("周五夜间");
        assertThat(calculator.actual(model, oneMillionInput, overnight)).isEqualByComparingTo("1.500000");
    }

    @Test
    void ignoresIndependentSupplierCostScheduleWhenCalculatingQuota() {
        var costPolicy = new CostTimePricingPolicy("Asia/Shanghai", List.of(
                new CostTimePricingRule(
                        "供应商峰时", List.of(DayOfWeek.MONDAY), LocalTime.of(9, 0), LocalTime.of(18, 0),
                        new CostRates(amount("9"), amount("9"), amount("9"), amount("9"))
                )
        ));
        ResolvedModelConfig model = model(
                new QuotaRates(amount("1"), amount("1"), amount("1"), amount("1"), amount("0")),
                costPolicy,
                null
        );

        var snapshot = calculator.snapshot(model, atShanghai(DayOfWeek.MONDAY, 10));

        assertThat(snapshot.ruleName()).isNull();
        assertThat(calculator.actual(model, new TokenUsage(1_000_000, 0, 0, 0, 0), snapshot))
                .isEqualByComparingTo("1.000000");
    }

    private ResolvedModelConfig model(QuotaRates rates) {
        return model(rates, null);
    }

    private ResolvedModelConfig model(QuotaRates rates, QuotaTimePricingPolicy policy) {
        return model(rates, null, policy);
    }

    private ResolvedModelConfig model(
            QuotaRates rates,
            CostTimePricingPolicy costPolicy,
            QuotaTimePricingPolicy quotaPolicy
    ) {
        return new ResolvedModelConfig(
                "test-model", "Test", null, "pricing-v1", "provider", "OPENAI_COMPATIBLE",
                "https://api.example.com/v1", "TEST_KEY", "upstream-test",
                new ModelCapabilities(1_000, 100, true, true, true, true, false),
                "USD", new CostRates(amount("1"), amount("1"), amount("0"), amount("1")), costPolicy,
                rates, quotaPolicy, Instant.now()
        );
    }

    private QuotaTimePricingRule rule(
            String name,
            List<DayOfWeek> days,
            LocalTime start,
            LocalTime end,
            String multiplier
    ) {
        return new QuotaTimePricingRule(name, days, start, end, amount(multiplier));
    }

    private Instant atShanghai(DayOfWeek day, int hour) {
        int dayOfMonth = 6 + day.getValue();
        return ZonedDateTime.of(2026, 9, dayOfMonth, hour, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
                .toInstant();
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
