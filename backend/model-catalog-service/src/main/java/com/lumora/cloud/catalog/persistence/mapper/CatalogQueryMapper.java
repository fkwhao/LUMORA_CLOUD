package com.lumora.cloud.catalog.persistence.mapper;

import com.lumora.cloud.catalog.persistence.entity.ModelVersionEntity;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CatalogQueryMapper {

    @Select("""
            SELECT v.*, m.code AS model_code, p.code AS provider_code,
                   p.credential_reference AS active_credential_reference
            FROM model_config_version v
            JOIN model_definition m ON m.id = v.model_id
            JOIN model_provider p ON p.id = v.provider_id
            WHERE v.status = 'PUBLISHED' AND m.status = 'ACTIVE' AND p.status = 'ACTIVE'
            ORDER BY v.display_name, m.code
            """)
    List<ModelVersionEntity> findPublishedModels();

    @Select("""
            SELECT COUNT(*)
            FROM model_config_version v
            JOIN model_definition m ON m.id = v.model_id
            JOIN model_provider p ON p.id = v.provider_id
            WHERE v.status = 'PUBLISHED' AND m.status = 'ACTIVE' AND p.status = 'ACTIVE'
            """)
    long countPublicModels();
}
