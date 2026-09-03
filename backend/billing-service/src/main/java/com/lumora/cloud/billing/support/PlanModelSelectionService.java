package com.lumora.cloud.billing.support;

import com.lumora.cloud.api.catalog.CatalogClient;
import com.lumora.cloud.api.catalog.CatalogContracts.PublishedModelReference;
import com.lumora.cloud.billing.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class PlanModelSelectionService {

    private final CatalogClient catalogClient;

    public PlanModelSelectionService(CatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    public List<String> normalizeAndValidate(List<String> modelCodes) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (modelCodes != null) {
            modelCodes.stream()
                    .map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
                    .filter(value -> !value.isBlank())
                    .forEach(normalized::add);
        }
        if (normalized.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLAN_MODELS_REQUIRED", "套餐至少需要包含一个模型");
        }
        List<PublishedModelReference> published;
        try {
            published = catalogClient.publishedModelReferences();
        } catch (RuntimeException error) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_CATALOG_UNAVAILABLE",
                    "暂时无法校验套餐模型，请稍后重试");
        }
        Set<String> available = published.stream().map(PublishedModelReference::modelCode).collect(
                java.util.stream.Collectors.toSet()
        );
        List<String> unavailable = normalized.stream().filter(code -> !available.contains(code)).toList();
        if (!unavailable.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_MODEL_NOT_PUBLISHED",
                    "套餐包含未发布或已停用的模型：" + String.join("、", unavailable));
        }
        return List.copyOf(normalized);
    }
}
