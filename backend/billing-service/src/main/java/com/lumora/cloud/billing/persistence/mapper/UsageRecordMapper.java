package com.lumora.cloud.billing.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.billing.persistence.entity.UsageRecordEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;

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
