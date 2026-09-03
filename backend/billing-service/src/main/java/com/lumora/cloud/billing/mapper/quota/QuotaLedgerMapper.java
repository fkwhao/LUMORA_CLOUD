package com.lumora.cloud.billing.mapper.quota;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.billing.domain.entity.quota.QuotaLedgerEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

public interface QuotaLedgerMapper extends BaseMapper<QuotaLedgerEntity> {

    @Select("""
            SELECT * FROM quota_ledger
            WHERE user_id = #{userId}
              AND created_at >= #{startsAt}
              AND created_at < #{endsAt}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<QuotaLedgerEntity> findRecentByUserBetween(
            @Param("userId") Long userId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("limit") int limit
    );

    @Select("""
            SELECT * FROM quota_ledger
            WHERE quota_bucket_id = #{bucketId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<QuotaLedgerEntity> findRecentByBucket(
            @Param("bucketId") String bucketId,
            @Param("limit") int limit
    );

    @Select("SELECT COUNT(*) FROM quota_ledger WHERE quota_bucket_id = #{bucketId}")
    long countByBucket(@Param("bucketId") String bucketId);
}
