package com.lumora.cloud.catalog.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.catalog.persistence.entity.ModelRouteEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ModelRouteMapper extends BaseMapper<ModelRouteEntity> {

    @Select("""
            SELECT r.*, p.code AS provider_code, p.name AS provider_name, p.status AS provider_status,
                   p.credential_reference AS active_credential_reference,
                   p.max_concurrency AS account_max_concurrency,
                   p.requests_per_minute AS account_requests_per_minute,
                   p.tokens_per_minute AS account_tokens_per_minute
            FROM model_upstream_route r
            JOIN model_provider p ON p.id = r.provider_id
            WHERE r.version_id = #{versionId}
            ORDER BY r.route_priority, r.is_primary DESC, r.created_at, r.id
            """)
    List<ModelRouteEntity> findByVersionId(@Param("versionId") String versionId);

    @Select("SELECT * FROM model_upstream_route WHERE id = #{id} AND version_id = #{versionId} LIMIT 1 FOR UPDATE")
    ModelRouteEntity findForUpdate(@Param("id") String id, @Param("versionId") String versionId);

    @Select("SELECT * FROM model_upstream_route WHERE version_id = #{versionId} AND is_primary = TRUE LIMIT 1 FOR UPDATE")
    ModelRouteEntity findPrimaryForUpdate(@Param("versionId") String versionId);

    @Update("""
            UPDATE model_upstream_route
            SET provider_id = #{entity.providerId}, route_name = #{entity.routeName},
                upstream_model = #{entity.upstreamModel}, protocol_type = #{entity.protocolType},
                base_url = #{entity.baseUrl}, credential_reference = #{entity.credentialReference},
                route_priority = #{entity.priority}, route_weight = #{entity.weight},
                max_concurrency = #{entity.maxConcurrency}, requests_per_minute = #{entity.requestsPerMinute},
                tokens_per_minute = #{entity.tokensPerMinute}, failover_enabled = #{entity.failoverEnabled},
                circuit_breaker_enabled = #{entity.circuitBreakerEnabled}, status = #{entity.status},
                cost_currency = #{entity.costCurrency}, input_cost_per_million = #{entity.inputCostPerMillion},
                output_cost_per_million = #{entity.outputCostPerMillion},
                cache_read_cost_per_million = #{entity.cacheReadCostPerMillion},
                cache_write_cost_per_million = #{entity.cacheWriteCostPerMillion},
                cost_time_pricing_enabled = #{entity.costTimePricingEnabled},
                cost_time_pricing_zone = #{entity.costTimePricingZone}, revision = revision + 1
            WHERE id = #{entity.id} AND version_id = #{entity.versionId}
              AND revision = #{expectedRevision}
            """)
    int updateOptimistic(
            @Param("entity") ModelRouteEntity entity,
            @Param("expectedRevision") long expectedRevision
    );

    @Delete("DELETE FROM model_upstream_route WHERE id = #{id} AND version_id = #{versionId} AND revision = #{revision} AND is_primary = FALSE")
    int deleteSecondaryOptimistic(
            @Param("id") String id,
            @Param("versionId") String versionId,
            @Param("revision") long revision
    );
}
