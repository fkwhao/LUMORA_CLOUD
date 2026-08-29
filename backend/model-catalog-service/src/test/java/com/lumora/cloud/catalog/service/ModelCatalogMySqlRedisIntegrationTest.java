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
import com.lumora.cloud.catalog.web.CatalogWebContracts.ModelVersionInput;
import com.lumora.cloud.catalog.web.CatalogWebContracts.PublishDraftRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.RotateProviderCredentialRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.UpdateDraftRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.UpdateModelStatusRequest;
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

    private static final String CACHE_KEY = "lumora:catalog:published:v1";
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

        var firstPublished = modelService.publishDraft(
                created.modelId(), new PublishDraftRequest(created.draft().revision())
        );
        String firstPricingVersion = firstPublished.published().pricingVersion();

        var secondDraft = modelService.createDraft(created.modelId());
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
        assertThat(credentialService.resolve(internalModel.credentialReference()).secret())
                .isEqualTo(firstApiKey);
        var encrypted = credentialMapper.findByReference(internalModel.credentialReference());
        assertThat(encrypted).isNotNull();
        assertThat(new String(encrypted.getEncryptedSecret(), java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain(firstApiKey);
        assertThat(publishedCatalogService.publicModels())
                .filteredOn(model -> model.code().equals("it-model-" + suffix))
                .singleElement()
                .satisfies(model -> assertThat(model.pricingVersion())
                        .isEqualTo(secondPublished.published().pricingVersion()));

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
        return new ModelVersionInput(
                displayName, "integration test", "upstream-test", 128_000, 8_192,
                true, true, true, true, "CNY",
                zero, zero, zero, zero, zero,
                quotaRate, quotaRate, quotaRate, quotaRate, quotaRate, new BigDecimal("0.100000")
        );
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
