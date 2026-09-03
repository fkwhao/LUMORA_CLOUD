package com.lumora.cloud.modelgateway.routing;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelRoute;
import com.lumora.cloud.modelgateway.config.RouteProtectionProperties;
import com.lumora.cloud.modelgateway.provider.ProviderCall;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteCircuitBreakerTest {

    @AfterEach
    void clearRules() {
        DegradeRuleManager.loadRules(List.of());
    }

    @Test
    void restoresDynamicRouteRuleAfterExternalRulesAreRefreshed() {
        RouteProtectionProperties properties = new RouteProtectionProperties();
        properties.setFailureRatio(0.4D);
        properties.setMinimumCalls(5);
        properties.setStatisticalWindowSeconds(20);
        properties.setOpenDurationSeconds(8);
        RouteCircuitBreaker breaker = new RouteCircuitBreaker(properties);

        invoke(breaker);
        assertThat(DegradeRuleManager.getRules())
                .extracting(DegradeRule::getResource)
                .containsExactly(RouteCircuitBreaker.resource("route-a"));

        DegradeRule feignRule = new DegradeRule("GET:http://catalog/internal/catalog/models/{modelCode}")
                .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
                .setCount(0.5D)
                .setMinRequestAmount(5)
                .setStatIntervalMs(30_000)
                .setTimeWindow(10);
        DegradeRuleManager.loadRules(List.of(feignRule));

        invoke(breaker);
        assertThat(DegradeRuleManager.getRules())
                .extracting(DegradeRule::getResource)
                .containsExactlyInAnyOrder(
                        feignRule.getResource(),
                        RouteCircuitBreaker.resource("route-a")
                );
    }

    private void invoke(RouteCircuitBreaker breaker) {
        breaker.protect(route(), () -> Mono.just(new ProviderCall(
                        HttpStatus.OK,
                        HttpHeaders.EMPTY,
                        Flux.empty()
                )))
                .flatMap(call -> call.body().then(Mono.just(call)))
                .block();
    }

    private ResolvedModelRoute route() {
        return new ResolvedModelRoute(
                "route-a", "Route A", 1L, "provider", "OPENAI_COMPATIBLE",
                "https://api.example.com/v1", "credential", "model", 100, 100,
                null, null, null, null, null, null,
                true, true, "USD", null, null
        );
    }
}
