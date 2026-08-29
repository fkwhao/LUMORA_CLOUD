package com.lumora.cloud.modelgateway.service;

import com.lumora.cloud.api.catalog.CatalogContracts.ModelCapabilities;
import com.lumora.cloud.api.catalog.CatalogContracts.QuotaRates;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.modelgateway.domain.TokenUsage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class QuotaCalculatorTest {

    private final QuotaCalculator calculator = new QuotaCalculator();

    @Test
    void reservesWorstCaseRateButSettlesAuthoritativeSplit() {
        ResolvedModelConfig model = model(new QuotaRates(
                amount("2"), amount("4"), amount("5"), amount("1"), amount("3"), amount("0.000001")
        ));

        assertThat(calculator.maximum(model, 100))
                .isEqualByComparingTo("0.003500");
        assertThat(calculator.actual(model, new TokenUsage(500, 40, 10, 200, 100)))
                .isEqualByComparingTo("0.001710");
    }

    @Test
    void appliesMinimumRequestQuota() {
        ResolvedModelConfig model = model(new QuotaRates(
                amount("1"), amount("1"), amount("1"), amount("1"), amount("1"), amount("0.25")
        ));

        assertThat(calculator.actual(model, new TokenUsage(1, 0, 0, 0, 0)))
                .isEqualByComparingTo("0.250000");
    }

    private ResolvedModelConfig model(QuotaRates rates) {
        return new ResolvedModelConfig(
                "test-model", "Test", null, "pricing-v1", "provider", "OPENAI_COMPATIBLE",
                "https://api.example.com/v1", "TEST_KEY", "upstream-test",
                new ModelCapabilities(1_000, 100, true, true, true, true), rates, Instant.now()
        );
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
