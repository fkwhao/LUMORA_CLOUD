package com.lumora.cloud.billing.mapper.quota;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.billing.domain.entity.quota.QuotaBucketEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

public interface QuotaBucketMapper extends BaseMapper<QuotaBucketEntity> {

    @Select("""
            SELECT * FROM quota_bucket
            WHERE subscription_id = #{subscriptionId} AND period_no = #{periodNo}
            LIMIT 1
            FOR UPDATE
            """)
    QuotaBucketEntity findPeriodForUpdate(
            @Param("subscriptionId") String subscriptionId,
            @Param("periodNo") int periodNo
    );

    @Select("SELECT * FROM quota_bucket WHERE id = #{id} LIMIT 1 FOR UPDATE")
    QuotaBucketEntity findByIdForUpdate(@Param("id") String id);

    @Update("""
            UPDATE quota_bucket
            SET reserved_quota = reserved_quota + #{amount}
            WHERE id = #{bucketId}
              AND granted_quota - reserved_quota - consumed_quota >= #{amount}
            """)
    int reserve(@Param("bucketId") String bucketId, @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE quota_bucket
            SET reserved_quota = reserved_quota - #{reservedAmount},
                consumed_quota = consumed_quota + #{billedAmount}
            WHERE id = #{bucketId}
              AND reserved_quota >= #{reservedAmount}
              AND #{billedAmount} <= #{reservedAmount}
            """)
    int settle(
            @Param("bucketId") String bucketId,
            @Param("reservedAmount") BigDecimal reservedAmount,
            @Param("billedAmount") BigDecimal billedAmount
    );

    @Update("""
            UPDATE quota_bucket
            SET reserved_quota = reserved_quota - #{amount}
            WHERE id = #{bucketId} AND reserved_quota >= #{amount}
            """)
    int release(@Param("bucketId") String bucketId, @Param("amount") BigDecimal amount);
}
