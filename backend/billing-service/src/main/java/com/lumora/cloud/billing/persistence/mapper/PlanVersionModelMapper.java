package com.lumora.cloud.billing.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.billing.persistence.entity.PlanVersionModelEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface PlanVersionModelMapper extends BaseMapper<PlanVersionModelEntity> {

    @Select("""
            SELECT model_code FROM billing_plan_version_model
            WHERE plan_version_id = #{planVersionId}
            ORDER BY display_order ASC
            """)
    List<String> findModelCodes(@Param("planVersionId") Long planVersionId);

    @Select("""
            SELECT EXISTS(
                SELECT 1 FROM billing_plan_version_model
                WHERE plan_version_id = #{planVersionId} AND model_code = #{modelCode}
            )
            """)
    boolean containsModel(@Param("planVersionId") Long planVersionId, @Param("modelCode") String modelCode);
}
