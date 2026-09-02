package com.lumora.cloud.modelgateway.routing;

import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelRoute;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class UpstreamRouteSelector {

    public List<ResolvedModelRoute> orderedCandidates(ResolvedModelConfig model) {
        if (model.routes() == null || model.routes().isEmpty()) {
            return List.of(new ResolvedModelRoute(
                    "legacy:" + model.modelCode(), "兼容默认路由", null, model.providerCode(),
                    model.protocolType(), model.baseUrl(), model.credentialReference(), model.upstreamModel(),
                    100, 100, null, null, null, null, null, null, true, true,
                    model.costCurrency(), model.costRates(), model.costTimePricingPolicy()
            ));
        }
        List<ResolvedModelRoute> source = model.routes().stream()
                .sorted(Comparator.comparingInt(ResolvedModelRoute::priority))
                .toList();
        List<ResolvedModelRoute> result = new ArrayList<>(source.size());
        int cursor = 0;
        while (cursor < source.size()) {
            int priority = source.get(cursor).priority();
            List<ResolvedModelRoute> group = new ArrayList<>();
            while (cursor < source.size() && source.get(cursor).priority() == priority) {
                group.add(source.get(cursor++));
            }
            appendWeightedShuffle(group, result);
        }
        return List.copyOf(result);
    }

    private void appendWeightedShuffle(List<ResolvedModelRoute> candidates, List<ResolvedModelRoute> target) {
        while (!candidates.isEmpty()) {
            long total = candidates.stream().mapToLong(route -> Math.max(1, route.weight())).sum();
            long selected = ThreadLocalRandom.current().nextLong(total);
            long cursor = 0;
            int index = 0;
            for (; index < candidates.size(); index++) {
                cursor += Math.max(1, candidates.get(index).weight());
                if (selected < cursor) {
                    break;
                }
            }
            target.add(candidates.remove(Math.min(index, candidates.size() - 1)));
        }
    }
}
