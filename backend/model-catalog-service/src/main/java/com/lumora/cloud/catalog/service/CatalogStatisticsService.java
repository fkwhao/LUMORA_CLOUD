package com.lumora.cloud.catalog.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lumora.cloud.catalog.persistence.entity.ModelDefinitionEntity;
import com.lumora.cloud.catalog.persistence.entity.ModelVersionEntity;
import com.lumora.cloud.catalog.persistence.entity.ProviderEntity;
import com.lumora.cloud.catalog.persistence.mapper.CatalogQueryMapper;
import com.lumora.cloud.catalog.persistence.mapper.ModelDefinitionMapper;
import com.lumora.cloud.catalog.persistence.mapper.ModelVersionMapper;
import com.lumora.cloud.catalog.persistence.mapper.ProviderMapper;
import com.lumora.cloud.catalog.web.CatalogWebContracts.AdminCatalogStatisticsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CatalogStatisticsService {

    private final ProviderMapper providerMapper;
    private final ModelDefinitionMapper modelMapper;
    private final ModelVersionMapper versionMapper;
    private final CatalogQueryMapper queryMapper;

    public CatalogStatisticsService(
            ProviderMapper providerMapper,
            ModelDefinitionMapper modelMapper,
            ModelVersionMapper versionMapper,
            CatalogQueryMapper queryMapper
    ) {
        this.providerMapper = providerMapper;
        this.modelMapper = modelMapper;
        this.versionMapper = versionMapper;
        this.queryMapper = queryMapper;
    }

    @Transactional(readOnly = true)
    public AdminCatalogStatisticsResponse statistics() {
        return new AdminCatalogStatisticsResponse(
                providerMapper.selectCount(Wrappers.<ProviderEntity>lambdaQuery()),
                providerMapper.selectCount(Wrappers.<ProviderEntity>lambdaQuery()
                        .eq(ProviderEntity::getStatus, "ACTIVE")),
                modelMapper.selectCount(Wrappers.<ModelDefinitionEntity>lambdaQuery()),
                queryMapper.countPublicModels(),
                versionMapper.selectCount(Wrappers.<ModelVersionEntity>lambdaQuery()
                        .eq(ModelVersionEntity::getStatus, "DRAFT")),
                versionMapper.selectCount(Wrappers.<ModelVersionEntity>lambdaQuery()),
                Instant.now()
        );
    }
}
