package com.lumora.cloud.catalog.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lumora.cloud.catalog.domain.entity.model.ModelDefinitionEntity;
import com.lumora.cloud.catalog.domain.entity.model.ModelVersionEntity;
import com.lumora.cloud.catalog.domain.entity.provider.ProviderEntity;
import com.lumora.cloud.catalog.mapper.query.CatalogQueryMapper;
import com.lumora.cloud.catalog.mapper.model.ModelDefinitionMapper;
import com.lumora.cloud.catalog.mapper.model.ModelVersionMapper;
import com.lumora.cloud.catalog.mapper.provider.ProviderMapper;
import com.lumora.cloud.catalog.domain.vo.statistics.AdminCatalogStatisticsResponse;
import com.lumora.cloud.catalog.service.ICatalogStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CatalogStatisticsServiceImpl implements ICatalogStatisticsService {

    private final ProviderMapper providerMapper;
    private final ModelDefinitionMapper modelMapper;
    private final ModelVersionMapper versionMapper;
    private final CatalogQueryMapper queryMapper;

    @Transactional(readOnly = true)
    @Override
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
