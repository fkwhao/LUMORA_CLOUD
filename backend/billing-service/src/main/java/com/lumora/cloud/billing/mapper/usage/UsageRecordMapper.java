package com.lumora.cloud.billing.mapper.usage;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.billing.domain.entity.usage.UsageRecordEntity;
import com.lumora.cloud.billing.domain.projection.history.UsageAggregate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface UsageRecordMapper extends BaseMapper<UsageRecordEntity> {

    @Insert("""
            INSERT INTO billing_usage_record (
                id, usage_id, reservation_id, request_id, user_id, model_code,
                pricing_version, input_tokens, output_tokens, reasoning_tokens,
                cache_read_tokens, cache_write_tokens, billed_quota, status, occurred_at
            ) VALUES (
                #{id}, #{usageId}, #{reservationId}, #{requestId}, #{userId}, #{modelCode},
                #{pricingVersion}, #{inputTokens}, #{outputTokens}, #{reasoningTokens},
                #{cacheReadTokens}, #{cacheWriteTokens}, #{billedQuota}, #{status}, #{occurredAt}
            )
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIdempotent(UsageRecordEntity entity);

    @Select("SELECT * FROM billing_usage_record WHERE usage_id = #{usageId} LIMIT 1 FOR UPDATE")
    UsageRecordEntity findByUsageIdForUpdate(@Param("usageId") String usageId);

    @Select("SELECT * FROM billing_usage_record WHERE reservation_id = #{reservationId} LIMIT 1 FOR UPDATE")
    UsageRecordEntity findByReservationIdForUpdate(@Param("reservationId") String reservationId);

    @Update("UPDATE billing_usage_record SET status = 'COMPLETED' WHERE id = #{id} AND status = 'PROCESSING'")
    int markCompleted(@Param("id") String id);

    @Update("""
            UPDATE billing_usage_record
            SET status = 'PENDING_RECONCILIATION'
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int markPending(@Param("id") String id);

    @Select("""
            SELECT * FROM billing_usage_record
            WHERE user_id = #{userId}
              AND occurred_at >= #{startsAt}
              AND occurred_at < #{endsAt}
            ORDER BY occurred_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<UsageRecordEntity> findRecentByUserBetween(
            @Param("userId") Long userId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("limit") int limit
    );

    @Select("""
            SELECT
                COUNT(*) AS request_count,
                COALESCE(SUM(status = 'COMPLETED'), 0) AS completed_count,
                COALESCE(SUM(status IN ('PROCESSING', 'PENDING_RECONCILIATION')), 0) AS pending_count,
                COALESCE(SUM(status = 'FAILED'), 0) AS failed_count,
                COALESCE(SUM(input_tokens), 0) AS input_tokens,
                COALESCE(SUM(output_tokens), 0) AS output_tokens,
                COALESCE(SUM(reasoning_tokens), 0) AS reasoning_tokens,
                COALESCE(SUM(cache_read_tokens), 0) AS cache_read_tokens,
                COALESCE(SUM(cache_write_tokens), 0) AS cache_write_tokens,
                COALESCE(SUM(billed_quota), 0) AS billed_quota
            FROM billing_usage_record
            WHERE user_id = #{userId}
              AND occurred_at >= #{startsAt}
              AND occurred_at < #{endsAt}
            """)
    UsageAggregate aggregateUserBetween(
            @Param("userId") Long userId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt
    );

    @Select("""
            SELECT COUNT(*) FROM billing_usage_record
            WHERE occurred_at >= #{startsAt} AND occurred_at < #{endsAt}
            """)
    long countBetween(@Param("startsAt") Instant startsAt, @Param("endsAt") Instant endsAt);

    @Select("""
            SELECT COUNT(*) FROM billing_usage_record
            WHERE status = #{status} AND occurred_at >= #{startsAt} AND occurred_at < #{endsAt}
            """)
    long countByStatusBetween(
            @Param("status") String status,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt
    );

    @Select("""
            SELECT COALESCE(SUM(billed_quota), 0) FROM billing_usage_record
            WHERE occurred_at >= #{startsAt} AND occurred_at < #{endsAt}
            """)
    BigDecimal sumBilledQuotaBetween(
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt
    );
}
