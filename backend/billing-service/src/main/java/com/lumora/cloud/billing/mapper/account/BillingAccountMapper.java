package com.lumora.cloud.billing.mapper.account;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.billing.domain.entity.account.BillingAccountEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BillingAccountMapper extends BaseMapper<BillingAccountEntity> {

    @Insert("""
            INSERT INTO billing_account (user_id)
            VALUES (#{userId})
            ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)
            """)
    int ensureExists(@Param("userId") Long userId);

    @Select("SELECT * FROM billing_account WHERE user_id = #{userId} LIMIT 1 FOR UPDATE")
    BillingAccountEntity findByUserIdForUpdate(@Param("userId") Long userId);
}
