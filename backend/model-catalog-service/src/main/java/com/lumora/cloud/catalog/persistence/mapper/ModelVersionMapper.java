package com.lumora.cloud.catalog.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.catalog.persistence.entity.ModelVersionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

public interface ModelVersionMapper extends BaseMapper<ModelVersionEntity> {

    @Select("""
            SELECT * FROM model_config_version
            WHERE model_id = #{modelId} AND status = 'DRAFT'
            LIMIT 1 FOR UPDATE
            """)
    ModelVersionEntity findDraftForUpdate(@Param("modelId") Long modelId);

    @Select("""
            SELECT * FROM model_config_version
            WHERE model_id = #{modelId} AND status = 'PUBLISHED'
            LIMIT 1 FOR UPDATE
            """)
    ModelVersionEntity findPublishedForUpdate(@Param("modelId") Long modelId);

    @Select("""
            SELECT * FROM model_config_version
            WHERE model_id = #{modelId} AND status = 'DRAFT'
            LIMIT 1
            """)
    ModelVersionEntity findDraft(@Param("modelId") Long modelId);

    @Select("""
            SELECT * FROM model_config_version
            WHERE model_id = #{modelId} AND status = 'PUBLISHED'
            LIMIT 1
            """)
    ModelVersionEntity findPublished(@Param("modelId") Long modelId);

    @Select("""
            SELECT * FROM model_config_version
            WHERE model_id = #{modelId}
            ORDER BY version_no DESC
            """)
    List<ModelVersionEntity> findAllByModelId(@Param("modelId") Long modelId);

    @Select("SELECT COALESCE(MAX(version_no), 0) FROM model_config_version WHERE model_id = #{modelId}")
    int maxVersionNo(@Param("modelId") Long modelId);

    @Update("""
            UPDATE model_config_version
            SET provider_id = #{entity.providerId}, display_name = #{entity.displayName},
                description = #{entity.description}, upstream_model = #{entity.upstreamModel},
                protocol_type = #{entity.protocolType}, base_url = #{entity.baseUrl},
                credential_reference = #{entity.credentialReference},
                context_window = #{entity.contextWindow}, max_output_tokens = #{entity.maxOutputTokens},
                supports_reasoning = #{entity.supportsReasoning}, supports_tools = #{entity.supportsTools},
                supports_vision = #{entity.supportsVision}, supports_json = #{entity.supportsJson},
                cost_currency = #{entity.costCurrency},
                input_cost_per_million = #{entity.inputCostPerMillion},
                output_cost_per_million = #{entity.outputCostPerMillion},
                reasoning_cost_per_million = #{entity.reasoningCostPerMillion},
                cache_read_cost_per_million = #{entity.cacheReadCostPerMillion},
                cache_write_cost_per_million = #{entity.cacheWriteCostPerMillion},
                input_quota_per_million = #{entity.inputQuotaPerMillion},
                output_quota_per_million = #{entity.outputQuotaPerMillion},
                reasoning_quota_per_million = #{entity.reasoningQuotaPerMillion},
                cache_read_quota_per_million = #{entity.cacheReadQuotaPerMillion},
                cache_write_quota_per_million = #{entity.cacheWriteQuotaPerMillion},
                minimum_request_quota = #{entity.minimumRequestQuota}, revision = revision + 1
            WHERE id = #{entity.id} AND status = 'DRAFT' AND revision = #{expectedRevision}
            """)
    int updateDraftOptimistic(
            @Param("entity") ModelVersionEntity entity,
            @Param("expectedRevision") long expectedRevision
    );

    @Update("""
            UPDATE model_config_version
            SET status = 'ARCHIVED'
            WHERE model_id = #{modelId} AND status = 'PUBLISHED'
            """)
    int archivePublished(@Param("modelId") Long modelId);

    @Update("""
            UPDATE model_config_version
            SET status = 'PUBLISHED', published_at = #{publishedAt}, revision = revision + 1
            WHERE id = #{id} AND status = 'DRAFT' AND revision = #{expectedRevision}
            """)
    int publishDraft(
            @Param("id") String id,
            @Param("expectedRevision") long expectedRevision,
            @Param("publishedAt") Instant publishedAt
    );
}
