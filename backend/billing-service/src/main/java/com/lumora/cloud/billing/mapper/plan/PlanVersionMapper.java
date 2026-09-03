package com.lumora.cloud.billing.mapper.plan;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.billing.domain.entity.plan.PlanVersionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface PlanVersionMapper extends BaseMapper<PlanVersionEntity> {

    @Select("""
            SELECT * FROM billing_plan_version
            WHERE id = #{id} AND status = 'PUBLISHED'
            LIMIT 1
            """)
    PlanVersionEntity findPublishedById(@Param("id") Long id);

    @Select("""
            SELECT * FROM billing_plan_version
            WHERE plan_id = #{planId} AND status = 'PUBLISHED'
            ORDER BY version_no DESC
            LIMIT 1
            """)
    PlanVersionEntity findLatestPublished(@Param("planId") Long planId);

    @Select("SELECT COALESCE(MAX(version_no), 0) FROM billing_plan_version WHERE plan_id = #{planId}")
    int maxVersionNo(@Param("planId") Long planId);

    @Select("""
            SELECT * FROM billing_plan_version
            WHERE plan_id = #{planId}
            ORDER BY version_no DESC
            """)
    List<PlanVersionEntity> findAllByPlanId(@Param("planId") Long planId);
}
