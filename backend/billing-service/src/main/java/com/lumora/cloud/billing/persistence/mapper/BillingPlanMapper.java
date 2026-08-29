package com.lumora.cloud.billing.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.billing.persistence.entity.BillingPlanEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BillingPlanMapper extends BaseMapper<BillingPlanEntity> {

    @Select("SELECT * FROM billing_plan WHERE code = #{code} LIMIT 1 FOR UPDATE")
    BillingPlanEntity findByCodeForUpdate(@Param("code") String code);

    @Select("SELECT * FROM billing_plan WHERE id = #{id} LIMIT 1 FOR UPDATE")
    BillingPlanEntity findByIdForUpdate(@Param("id") Long id);
}
