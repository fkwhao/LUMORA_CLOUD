package com.lumora.cloud.catalog.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lumora.cloud.catalog.domain.ModelVersionValues;
import com.lumora.cloud.catalog.error.ApiException;
import com.lumora.cloud.catalog.persistence.entity.ModelVersionEntity;
import com.lumora.cloud.catalog.persistence.entity.ProviderEntity;
import com.lumora.cloud.catalog.persistence.mapper.CatalogQueryMapper;
import com.lumora.cloud.catalog.persistence.mapper.ModelVersionMapper;
import com.lumora.cloud.catalog.persistence.mapper.ProviderMapper;
import com.lumora.cloud.catalog.persistence.mapper.ProviderCredentialMapper;
import com.lumora.cloud.catalog.web.CatalogWebContracts.CreateModelRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.CreateProviderRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.CostRateInput;
import com.lumora.cloud.catalog.web.CatalogWebContracts.CostTimePricingPolicyInput;
import com.lumora.cloud.catalog.web.CatalogWebContracts.CostTimePricingRuleInput;
import com.lumora.cloud.catalog.web.CatalogWebContracts.ModelVersionInput;
import com.lumora.cloud.catalog.web.CatalogWebContracts.ModelRouteInput;
import com.lumora.cloud.catalog.web.CatalogWebContracts.PublishDraftRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.QuotaTimePricingPolicyInput;
import com.lumora.cloud.catalog.web.CatalogWebContracts.QuotaTimePricingRuleInput;
import com.lumora.cloud.catalog.web.CatalogWebContracts.RotateProviderCredentialRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.UpdateDraftRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.UpdateModelStatusRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.UpdateModelRouteRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "lumora.catalog.credentials.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "lumora.catalog.credentials.key-version=1"
        }
)
@Transactional
@EnabledIfEnvironmentVariable(named = "LUMORA_RUN_CATALOG_TESTS", matches = "true")
class ModelCatalogMySqlRedisIntegrationTest {

    private static final String CACHE_KEY = "lumora:catalog:published:v3";
    private static final String GENERATION_KEY = "lumora:catalog:generation";

    @Autowired
    private ProviderService providerService;

    @Autowired
    private ModelAdministrationService modelService;

    @Autowired
    private PublishedCatalogService publishedCatalogService;

    @Autowired
    private CatalogQueryMapper queryMapper;

    @Autowired
    private ProviderMapper providerMapper;

    @Autowired
    private ProviderCredentialMapper credentialMapper;

    @Autowired
    private ProviderCredentialService credentialService;

    @Autowired
    private ModelVersionMapper versionMapper;

    @Autowired
    private CatalogInputMapper inputMapper;

    @Autowired
    private CatalogStatisticsService statisticsService;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    @AfterEach
    void clearPublishedCache() {
        redis.delete(CACHE_KEY);
        redis.delete(GENERATION_KEY);
    }

    @Test
    void publishesImmutableVersionsAndRemovesDisabledModelFromReadPath() {
        var statisticsBefore = statisticsService.statistics();
        String suffix = suffix();
        String firstApiKey = "sk-integration-first-" + suffix;
        var provider = providerService.create(providerRequest(suffix));
        assertThat(provider.credential().managed()).isTrue();
        assertThat(provider.credential().maskedValue()).doesNotContain(firstApiKey);
        var created = modelService.create(new CreateModelRequest(
                "it-model-" + suffix, provider.id(), version("First", "1.000000")
        ));
        assertThat(created.draft().costTimePricingPolicy().zoneId()).isEqualTo("Asia/Shanghai");
        assertThat(created.draft().costTimePricingPolicy().rules()).hasSize(1);
        assertThat(created.draft().costTimePricingPolicy().rules().getFirst().startTime())
                .isEqualTo(LocalTime.of(9, 30));
        assertThat(created.draft().costTimePricingPolicy().rules().getFirst()
                .costRates().cacheCreationInputPerMillion())
                .isEqualByComparingTo("0.000000");
        assertThat(created.draft().quotaTimePricingPolicy().zoneId()).isEqualTo("Asia/Shanghai");
        assertThat(created.draft().quotaTimePricingPolicy().rules()).hasSize(2);
        assertThat(created.draft().routes()).singleElement().satisfies(route -> {
            assertThat(route.primary()).isTrue();
            assertThat(route.providerId()).isEqualTo(provider.id());
            assertThat(route.upstreamModel()).isEqualTo("upstream-test");
            assertThat(route.priority()).isEqualTo(100);
            assertThat(route.weight()).isEqualTo(100);
        });

        var firstPublished = modelService.publishDraft(
                created.modelId(), new PublishDraftRequest(created.draft().revision())
        );
        String firstPricingVersion = firstPublished.published().pricingVersion();

        var secondDraft = modelService.createDraft(created.modelId());
        assertThat(secondDraft.draft())
                .usingRecursiveComparison()
                .ignoringFields(
                        "id", "pricingVersion", "versionNo", "status", "revision",
                        "publishedAt", "createdAt", "updatedAt", "routes"
                )
                .isEqualTo(firstPublished.published());
        assertThat(secondDraft.draft().routes()).hasSameSizeAs(firstPublished.published().routes());
        assertThat(secondDraft.draft().routes().getFirst())
                .usingRecursiveComparison()
                .ignoringFields("id", "revision", "createdAt", "updatedAt")
                .isEqualTo(firstPublished.published().routes().getFirst());
        String discardedDraftId = secondDraft.draft().id();
        modelService.discardDraft(created.modelId(), secondDraft.draft().revision());
        assertThat(versionMapper.selectById(discardedDraftId)).isNull();
        assertThat(modelService.history(created.modelId()))
                .extracting(version -> version.versionNo() + ":" + version.status())
                .containsExactly("1:PUBLISHED");

        secondDraft = modelService.createDraft(created.modelId());
        assertThat(secondDraft.draft().versionNo()).isEqualTo(2);
        var updatedDraft = modelService.updateDraft(created.modelId(), new UpdateDraftRequest(
                secondDraft.draft().revision(), provider.id(), version("Second", "2.500000")
        ));
        var secondPublished = modelService.publishDraft(created.modelId(), new PublishDraftRequest(
                updatedDraft.draft().revision()
        ));

        assertThat(secondPublished.published().versionNo()).isEqualTo(2);
        assertThat(secondPublished.published().pricingVersion()).isNotEqualTo(firstPricingVersion);
        assertThat(queryMapper.findPublishedModels())
                .filteredOn(model -> model.getModelCode().equals("it-model-" + suffix))
                .singleElement()
                .satisfies(model -> {
                    assertThat(model.getVersionNo()).isEqualTo(2);
                    assertThat(model.getInputQuotaPerMillion()).isEqualByComparingTo("2.500000");
                });
        assertThat(versionMapper.selectList(Wrappers.<ModelVersionEntity>lambdaQuery()
                        .eq(ModelVersionEntity::getModelId, created.modelId())))
                .extracting(ModelVersionEntity::getStatus)
                .containsExactlyInAnyOrder("ARCHIVED", "PUBLISHED");
        assertThat(modelService.history(created.modelId()))
                .extracting(version -> version.versionNo() + ":" + version.status())
                .containsExactly("2:PUBLISHED", "1:ARCHIVED");

        var internalModel = publishedCatalogService.resolve("IT-MODEL-" + suffix);
        assertThat(internalModel.baseUrl()).isEqualTo("https://api.example.com/v1");
        assertThat(internalModel.credentialReference()).startsWith("cred_");
        assertThat(internalModel.costTimePricingPolicy().rules()).hasSize(1);
        assertThat(internalModel.costTimePricingPolicy().rules().getFirst().costRates().outputPerMillion())
                .isEqualByComparingTo("0.300000");
        assertThat(internalModel.quotaTimePricingPolicy().rules()).hasSize(2);
        assertThat(internalModel.routes()).singleElement().satisfies(route -> {
            assertThat(route.routeId()).isNotBlank();
            assertThat(route.providerCode()).isEqualTo(provider.code());
        });
        assertThat(internalModel.quotaTimePricingPolicy().rules().get(1).quotaMultiplier())
                .isEqualByComparingTo("1.200000");
        assertThat(credentialService.resolve(internalModel.credentialReference()).secret())
                .isEqualTo(firstApiKey);
        var encrypted = credentialMapper.findByReference(internalModel.credentialReference());
        assertThat(encrypted).isNotNull();
        assertThat(new String(encrypted.getEncryptedSecret(), java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain(firstApiKey);
        assertThat(publishedCatalogService.publicModels())
                .filteredOn(model -> model.code().equals("it-model-" + suffix))
                .singleElement()
                .satisfies(model -> {
                    assertThat(model.pricingVersion()).isEqualTo(secondPublished.published().pricingVersion());
                    assertThat(model.quotaTimePricingPolicy().rules()).hasSize(2);
                    assertThat(model.quotaTimePricingPolicy().rules().getFirst().quotaMultiplier())
                            .isEqualByComparingTo("1.200000");
                });

        var publishedStatistics = statisticsService.statistics();
        assertThat(publishedStatistics.totalProviders() - statisticsBefore.totalProviders()).isEqualTo(1);
        assertThat(publishedStatistics.activeProviders() - statisticsBefore.activeProviders()).isEqualTo(1);
        assertThat(publishedStatistics.totalModels() - statisticsBefore.totalModels()).isEqualTo(1);
        assertThat(publishedStatistics.publicModels() - statisticsBefore.publicModels()).isEqualTo(1);
        assertThat(publishedStatistics.totalVersions() - statisticsBefore.totalVersions()).isEqualTo(2);

        String rotatedApiKey = "sk-integration-rotated-" + suffix;
        var rotatedProvider = providerService.rotateCredential(provider.id(),
                new RotateProviderCredentialRequest(provider.revision(), rotatedApiKey));
        assertThat(rotatedProvider.revision()).isEqualTo(provider.revision() + 1);
        assertThat(rotatedProvider.credential().fingerprint()).isNotEqualTo(provider.credential().fingerprint());
        clearPublishedCache();
        var rotatedModel = publishedCatalogService.resolve("it-model-" + suffix);
        assertThat(rotatedModel.pricingVersion()).isEqualTo(secondPublished.published().pricingVersion());
        assertThat(rotatedModel.credentialReference()).isEqualTo(internalModel.credentialReference());
        assertThat(credentialService.resolve(rotatedModel.credentialReference()).secret())
                .isEqualTo(rotatedApiKey);

        redis.delete(CACHE_KEY);
        modelService.updateStatus(created.modelId(), new UpdateModelStatusRequest(
                secondPublished.revision(), "DISABLED"
        ));
        assertThat(queryMapper.findPublishedModels())
                .noneMatch(model -> model.getModelCode().equals("it-model-" + suffix));
        assertThatThrownBy(() -> publishedCatalogService.resolve("it-model-" + suffix))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("MODEL_NOT_AVAILABLE");
        assertThat(statisticsService.statistics().publicModels()).isEqualTo(statisticsBefore.publicModels());
    }

    @Test
    void discardingTheOnlyDraftRemovesTheUnpublishedModel() {
        String suffix = suffix();
        var provider = providerService.create(providerRequest(suffix));
        var created = modelService.create(new CreateModelRequest(
                "it-discard-" + suffix, provider.id(), version("Discard", "1")
        ));
        String draftId = created.draft().id();

        modelService.discardDraft(created.modelId(), created.draft().revision());

        assertThat(versionMapper.selectById(draftId)).isNull();
        assertThat(modelService.list())
                .noneMatch(model -> model.modelId().equals(created.modelId()));
    }

    @Test
    void updatesPrimaryRouteSchedulingWithoutOverwritingDraftManagedConfiguration() {
        String suffix = suffix();
        var provider = providerService.create(providerRequest(suffix));
        var created = modelService.create(new CreateModelRequest(
                "it-primary-route-" + suffix, provider.id(), version("Primary route", "1")
        ));
        var primary = created.draft().routes().getFirst();

        var updated = modelService.updateRoute(created.modelId(), primary.id(), new UpdateModelRouteRequest(
                primary.revision(), new ModelRouteInput(
                        "不应覆盖默认路由名称", provider.id(), "should-not-replace-upstream",
                        10, 250, 8, 600, 120_000L,
                        false, false, "ACTIVE", "USD",
                        new BigDecimal("99"), new BigDecimal("98"), new BigDecimal("97"),
                        new BigDecimal("96"), null
                )
        ));

        assertThat(updated.priority()).isEqualTo(10);
        assertThat(updated.weight()).isEqualTo(250);
        assertThat(updated.maxConcurrency()).isEqualTo(8);
        assertThat(updated.requestsPerMinute()).isEqualTo(600);
        assertThat(updated.tokensPerMinute()).isEqualTo(120_000L);
        assertThat(updated.failoverEnabled()).isFalse();
        assertThat(updated.circuitBreakerEnabled()).isFalse();
        assertThat(updated.routeName()).isEqualTo(primary.routeName());
        assertThat(updated.providerId()).isEqualTo(primary.providerId());
        assertThat(updated.upstreamModel()).isEqualTo(primary.upstreamModel());
        assertThat(updated.costCurrency()).isEqualTo(primary.costCurrency());
        assertThat(updated.costRates()).isEqualTo(primary.costRates());
        assertThat(updated.costTimePricingPolicy()).isEqualTo(primary.costTimePricingPolicy());
    }

    @Test
    void databaseConstraintPreventsTwoDraftsForOneModel() {
        String suffix = suffix();
        var providerResponse = providerService.create(providerRequest(suffix));
        var model = modelService.create(new CreateModelRequest(
                "it-guard-" + suffix, providerResponse.id(), version("Guard", "1")
        ));
        ProviderEntity provider = providerMapper.selectById(providerResponse.id());
        ModelVersionValues values = inputMapper.values(version("Guard duplicate", "1"));
        ModelVersionEntity duplicateDraft = ModelVersionEntity.draft(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), model.modelId(), 2, provider, values
        );

        assertThatThrownBy(() -> versionMapper.insert(duplicateDraft))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private CreateProviderRequest providerRequest(String suffix) {
        return new CreateProviderRequest(
                "it-provider-" + suffix,
                "Integration Provider",
                "OPENAI_COMPATIBLE",
                "https://api.example.com/v1/",
                "sk-integration-first-" + suffix
        );
    }

    private ModelVersionInput version(String displayName, String quota) {
        BigDecimal zero = BigDecimal.ZERO;
        BigDecimal quotaRate = new BigDecimal(quota);
        var costSchedule = new CostTimePricingPolicyInput(
                "Asia/Shanghai", List.of(
                        new CostTimePricingRuleInput(
                                "上午峰时", List.of(
                                        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
                                ), LocalTime.of(9, 30), LocalTime.of(12, 0),
                                new CostRateInput(new BigDecimal("0.10"), new BigDecimal("0.02"), null,
                                        new BigDecimal("0.30"))
                        )
                )
        );
        var quotaSchedule = new QuotaTimePricingPolicyInput(
                "Asia/Shanghai", BigDecimal.ONE, List.of(
                        new QuotaTimePricingRuleInput(
                                "上午额度峰时", List.of(
                                        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
                                ), LocalTime.of(8, 0), LocalTime.of(11, 30),
                                new BigDecimal("1.200000")
                        ),
                        new QuotaTimePricingRuleInput(
                                "下午峰时", List.of(
                                        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
                                ), LocalTime.of(14, 0), LocalTime.of(22, 0),
                                new BigDecimal("1.200000")
                        )
                )
        );
        return new ModelVersionInput(
                displayName, "integration test", "upstream-test", 128_000, 8_192,
                true, true, true, true, false, "CNY",
                zero, zero, null, zero, costSchedule,
                quotaRate, quotaRate, null, quotaRate, new BigDecimal("0.100000"), quotaSchedule
        );
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
