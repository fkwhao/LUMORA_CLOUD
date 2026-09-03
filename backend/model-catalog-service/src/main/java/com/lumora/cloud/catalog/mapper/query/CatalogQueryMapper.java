package com.lumora.cloud.catalog.mapper.query;

import com.lumora.cloud.catalog.domain.entity.model.ModelVersionEntity;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CatalogQueryMapper {

    @Select("""
            SELECT v.*, m.code AS model_code, p.code AS provider_code,
                   p.credential_reference AS active_credential_reference
            FROM model_config_version v
            JOIN model_definition m ON m.id = v.model_id
            JOIN model_provider p ON p.id = v.provider_id
            WHERE v.status = 'PUBLISHED' AND m.status = 'ACTIVE'
              AND EXISTS (
                  SELECT 1 FROM model_upstream_route r
                  JOIN model_provider route_provider ON route_provider.id = r.provider_id
                  WHERE r.version_id = v.id AND r.status = 'ACTIVE' AND route_provider.status = 'ACTIVE'
              )
            ORDER BY v.display_name, m.code
            """)
    List<ModelVersionEntity> findPublishedModels();

    @Select("""
            SELECT COUNT(*)
            FROM model_config_version v
            JOIN model_definition m ON m.id = v.model_id
            JOIN model_provider p ON p.id = v.provider_id
            WHERE v.status = 'PUBLISHED' AND m.status = 'ACTIVE'
              AND EXISTS (
                  SELECT 1 FROM model_upstream_route r
                  JOIN model_provider route_provider ON route_provider.id = r.provider_id
                  WHERE r.version_id = v.id AND r.status = 'ACTIVE' AND route_provider.status = 'ACTIVE'
              )
            """)
    long countPublicModels();
}
