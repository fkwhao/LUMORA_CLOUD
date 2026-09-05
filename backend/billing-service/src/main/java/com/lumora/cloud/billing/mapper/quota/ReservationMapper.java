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

    @Select("SELECT * FROM billing_reservation WHERE request_id = #{requestId} LIMIT 1")
    ReservationEntity findByRequestId(@Param("requestId") String requestId);

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
            WHERE status IN ('ACTIVE', 'PENDING_RECONCILIATION') AND hold_released = FALSE AND expires_at <= #{now}
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
            WHERE id = #{id} AND status IN ('ACTIVE', 'PENDING_RECONCILIATION')
            """)
    int markSettled(
            @Param("id") String id,
            @Param("settledQuota") BigDecimal settledQuota,
            @Param("settledAt") Instant settledAt
    );

    @Update("""
            UPDATE billing_reservation
            SET status = 'RELEASED', failure_reason = #{reason}, released_at = #{releasedAt}
            WHERE id = #{id} AND status IN ('ACTIVE', 'PENDING_RECONCILIATION')
            """)
    int markReleased(
            @Param("id") String id,
            @Param("reason") String reason,
            @Param("releasedAt") Instant releasedAt
    );

    @Update("""
            UPDATE billing_reservation
            SET status = 'PENDING_RECONCILIATION', failure_reason = #{reason}
            WHERE id = #{id} AND status IN ('ACTIVE', 'PENDING_RECONCILIATION')
            """)
    int markPending(@Param("id") String id, @Param("reason") String reason);

    @Update("""
            UPDATE billing_reservation
            SET status = 'PENDING_RECONCILIATION', hold_released = TRUE,
                failure_reason = '预占超时，已返还占用额度，等待可靠用量核对'
            WHERE id = #{id} AND status IN ('ACTIVE', 'PENDING_RECONCILIATION') AND hold_released = FALSE
            """)
    int expireHold(@Param("id") String id);

    @Select("""
            SELECT request_id FROM billing_reservation
            WHERE status = 'PENDING_RECONCILIATION'
              AND (reconciliation_next_at IS NULL OR reconciliation_next_at <= #{now})
            ORDER BY COALESCE(reconciliation_next_at, created_at), id
            LIMIT #{limit}
            """)
    java.util.List<String> findDueReconciliation(@Param("now") Instant now, @Param("limit") int limit);

    @Update("""
            UPDATE billing_reservation
            SET reconciliation_attempts = reconciliation_attempts + 1,
                reconciliation_checked_at = #{now}, reconciliation_next_at = #{next}, reconciliation_note = #{note}
            WHERE id = #{id} AND status = 'PENDING_RECONCILIATION'
            """)
    int recordReconciliationAttempt(@Param("id") String id, @Param("now") Instant now,
                                   @Param("next") Instant next, @Param("note") String note);

    @Update("""
            UPDATE billing_reservation
            SET reconciliation_attempts = reconciliation_attempts + 1,
                reconciliation_checked_at = #{now}, reconciliation_next_at = NULL, reconciliation_note = '已按记录用量自动结算'
            WHERE id = #{id} AND status = 'SETTLED'
            """)
    int recordReconciliationSuccess(@Param("id") String id, @Param("now") Instant now);

    @Select("""
            <script>
            SELECT * FROM billing_reservation
            WHERE status IN ('PENDING_RECONCILIATION', 'SETTLED', 'RELEASED')
            <if test="status != null">AND status = #{status}</if>
            <if test="requestId != null">AND request_id = #{requestId}</if>
            <if test="userId != null">AND user_id = #{userId}</if>
            ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    java.util.List<ReservationEntity> listReconciliation(@Param("status") String status,
            @Param("requestId") String requestId, @Param("userId") Long userId,
            @Param("offset") long offset, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM billing_reservation
            WHERE status IN ('PENDING_RECONCILIATION', 'SETTLED', 'RELEASED')
            <if test="status != null">AND status = #{status}</if>
            <if test="requestId != null">AND request_id = #{requestId}</if>
            <if test="userId != null">AND user_id = #{userId}</if>
            </script>
            """)
    long countReconciliation(@Param("status") String status, @Param("requestId") String requestId,
                             @Param("userId") Long userId);

}
