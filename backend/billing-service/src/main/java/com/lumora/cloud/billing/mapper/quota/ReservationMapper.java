package com.lumora.cloud.billing.mapper.quota;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.billing.domain.entity.quota.ReservationEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;

public interface ReservationMapper extends BaseMapper<ReservationEntity> {

    @Insert("""
            INSERT INTO billing_reservation (
                id, request_id, client_request_id, user_id, model_code, pricing_version,
                pricing_at, quota_multiplier, pricing_rule_name, requested_quota, status, expires_at
            ) VALUES (
                #{id}, #{requestId}, #{clientRequestId}, #{userId}, #{modelCode}, #{pricingVersion},
                #{pricingAt}, #{quotaMultiplier}, #{pricingRuleName}, #{requestedQuota}, #{status}, #{expiresAt}
            )
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIdempotent(ReservationEntity entity);

    @Select("SELECT * FROM billing_reservation WHERE request_id = #{requestId} LIMIT 1 FOR UPDATE")
    ReservationEntity findByRequestIdForUpdate(@Param("requestId") String requestId);

    @Select("""
            SELECT * FROM billing_reservation
            WHERE user_id = #{userId} AND client_request_id = #{clientRequestId}
            LIMIT 1
            FOR UPDATE
            """)
    ReservationEntity findByClientRequestForUpdate(
            @Param("userId") Long userId,
            @Param("clientRequestId") String clientRequestId
    );

    @Select("""
            SELECT request_id FROM billing_reservation
            WHERE status = 'ACTIVE' AND expires_at <= #{now}
            ORDER BY expires_at
            LIMIT #{limit}
            """)
    java.util.List<String> findExpiredActiveRequestIds(
            @Param("now") Instant now,
            @Param("limit") int limit
    );

    @Update("""
            UPDATE billing_reservation
            SET quota_bucket_id = #{bucketId}, status = 'ACTIVE', expires_at = #{expiresAt}
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int activate(
            @Param("id") String id,
            @Param("bucketId") String bucketId,
            @Param("expiresAt") Instant expiresAt
    );

    @Update("""
            UPDATE billing_reservation
            SET status = 'SETTLED', settled_quota = #{settledQuota}, settled_at = #{settledAt}
            WHERE id = #{id} AND status = 'ACTIVE'
            """)
    int markSettled(
            @Param("id") String id,
            @Param("settledQuota") BigDecimal settledQuota,
            @Param("settledAt") Instant settledAt
    );

    @Update("""
            UPDATE billing_reservation
            SET status = 'RELEASED', failure_reason = #{reason}, released_at = #{releasedAt}
            WHERE id = #{id} AND status = 'ACTIVE'
            """)
    int markReleased(
            @Param("id") String id,
            @Param("reason") String reason,
            @Param("releasedAt") Instant releasedAt
    );

    @Update("""
            UPDATE billing_reservation
            SET status = 'PENDING_RECONCILIATION', failure_reason = #{reason}
            WHERE id = #{id} AND status = 'ACTIVE'
            """)
    int markPending(@Param("id") String id, @Param("reason") String reason);
}
