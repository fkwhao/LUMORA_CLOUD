package com.lumora.cloud.modelgateway.routing;

import com.alibaba.csp.sentinel.AsyncEntry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelRoute;
import com.lumora.cloud.modelgateway.concurrency.RouteCapacityException;
import com.lumora.cloud.modelgateway.config.RouteProtectionProperties;
import com.lumora.cloud.modelgateway.error.ApiException;
import com.lumora.cloud.modelgateway.provider.ProviderHttpException;
import com.lumora.cloud.modelgateway.provider.ProviderCall;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class RouteCircuitBreaker {

    private static final String RESOURCE_PREFIX = "lumora:model-route:";

    private final RouteProtectionProperties properties;
    private final Map<String, DegradeRule> rules = new HashMap<>();

    public RouteCircuitBreaker(RouteProtectionProperties properties) {
        this.properties = properties;
    }

    public Mono<ProviderCall> protect(ResolvedModelRoute route, Supplier<Mono<ProviderCall>> operation) {
        if (!route.circuitBreakerEnabled()) {
            return Mono.defer(operation);
        }
        String resource = resource(route.routeId());
        ensureRule(resource);
        return Mono.defer(() -> {
            final AsyncEntry entry;
            try {
                entry = SphU.asyncEntry(resource);
            } catch (BlockException exception) {
                return Mono.error(new RouteCapacityException(
                        "MODEL_ROUTE_CIRCUIT_OPEN", "上游路由熔断中"
                ));
            }
            AtomicBoolean exited = new AtomicBoolean();
            Mono<ProviderCall> source;
            try {
                source = operation.get();
            } catch (Throwable error) {
                if (recordsFailure(error)) {
                    Tracer.traceEntry(error, entry);
                }
                exit(entry, exited);
                return Mono.error(error);
            }
            return source
                    .map(call -> new ProviderCall(
                            call.status(), call.headers(), call.body()
                            .doOnError(error -> {
                                if (recordsFailure(error)) {
                                    Tracer.traceEntry(error, entry);
                                }
                            })
                            .doFinally(signal -> exit(entry, exited))
                    ))
                    .switchIfEmpty(Mono.defer(() -> {
                        exit(entry, exited);
                        return Mono.error(new IllegalStateException("Upstream route returned no response"));
                    }))
                    .doOnError(error -> {
                        if (recordsFailure(error)) {
                            Tracer.traceEntry(error, entry);
                        }
                        exit(entry, exited);
                    })
                    .doOnCancel(() -> exit(entry, exited));
        });
    }

    private void exit(AsyncEntry entry, AtomicBoolean exited) {
        if (exited.compareAndSet(false, true)) {
            entry.exit();
        }
    }

    private synchronized void ensureRule(String resource) {
        DegradeRule activeRule = DegradeRuleManager.getRules().stream()
                .filter(existing -> resource.equals(existing.getResource()))
                .findFirst()
                .orElse(null);
        if (activeRule != null && matchesConfiguration(activeRule)) {
            return;
        }
        DegradeRule rule = new DegradeRule(resource)
                .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
                .setCount(properties.getFailureRatio())
                .setMinRequestAmount(properties.getMinimumCalls())
                .setStatIntervalMs(properties.getStatisticalWindowSeconds() * 1_000)
                .setTimeWindow(properties.getOpenDurationSeconds());
        rules.put(resource, rule);
        var merged = new java.util.ArrayList<>(DegradeRuleManager.getRules().stream()
                .filter(existing -> !existing.getResource().startsWith(RESOURCE_PREFIX))
                .toList());
        merged.addAll(rules.values());
        DegradeRuleManager.loadRules(merged);
    }

    private boolean matchesConfiguration(DegradeRule rule) {
        return rule.getGrade() == RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO
                && Double.compare(rule.getCount(), properties.getFailureRatio()) == 0
                && rule.getMinRequestAmount() == properties.getMinimumCalls()
                && rule.getStatIntervalMs() == properties.getStatisticalWindowSeconds() * 1_000
                && rule.getTimeWindow() == properties.getOpenDurationSeconds();
    }

    private boolean recordsFailure(Throwable error) {
        if (error instanceof ApiException) {
            return false;
        }
        if (error instanceof ProviderHttpException providerError) {
            int status = providerError.getStatus().value();
            return status == 401 || status == 403 || status == 408 || status == 429 || status >= 500;
        }
        return true;
    }

    public static String resource(String routeId) {
        return RESOURCE_PREFIX + routeId;
    }
}
