package com.lumora.cloud.catalog.mapper.pricing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.catalog.domain.entity.pricing.ModelTimePricingRuleEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ModelTimePricingRuleMapper extends BaseMapper<ModelTimePricingRuleEntity> {

    @Select("""
            SELECT * FROM model_time_pricing_rule
            WHERE version_id = #{versionId}
            ORDER BY rule_order
            """)
    List<ModelTimePricingRuleEntity> findByVersionId(@Param("versionId") String versionId);

    @Select("""
            SELECT * FROM model_time_pricing_rule
            WHERE version_id = #{versionId} AND pricing_scope = #{pricingScope}
            ORDER BY rule_order
            """)
    List<ModelTimePricingRuleEntity> findByVersionIdAndScope(
            @Param("versionId") String versionId,
            @Param("pricingScope") String pricingScope
    );

    @Delete("DELETE FROM model_time_pricing_rule WHERE version_id = #{versionId} AND pricing_scope = #{pricingScope}")
    int deleteByVersionIdAndScope(
            @Param("versionId") String versionId,
            @Param("pricingScope") String pricingScope
    );

    @Delete("DELETE FROM model_time_pricing_rule WHERE version_id = #{versionId}")
    int deleteByVersionId(@Param("versionId") String versionId);
}
