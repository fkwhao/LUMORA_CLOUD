package com.lumora.cloud.billing.mapper.usage;

import com.lumora.cloud.billing.domain.entity.usage.UsageRecordEntity;
import com.lumora.cloud.billing.domain.projection.history.DailyUsageAggregate;
import com.lumora.cloud.billing.domain.projection.history.UsageAggregate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface UsageDailySummaryMapper {

    @Insert("""
            INSERT INTO billing_usage_daily_summary (
                user_id, summary_date, request_count, completed_count, pending_count, failed_count,
                input_tokens, output_tokens, reasoning_tokens, cache_read_tokens, cache_write_tokens, billed_quota
            ) VALUES (
                #{usage.userId}, #{summaryDate}, 1,
                CASE WHEN #{status} = 'COMPLETED' THEN 1 ELSE 0 END,
                CASE WHEN #{status} IN ('PROCESSING', 'PENDING_RECONCILIATION') THEN 1 ELSE 0 END,
                CASE WHEN #{status} = 'FAILED' THEN 1 ELSE 0 END,
                #{usage.inputTokens}, #{usage.outputTokens}, #{usage.reasoningTokens},
                #{usage.cacheReadTokens}, #{usage.cacheWriteTokens}, #{usage.billedQuota}
            )
            ON DUPLICATE KEY UPDATE
                request_count = request_count + 1,
                completed_count = completed_count + VALUES(completed_count),
                pending_count = pending_count + VALUES(pending_count),
                failed_count = failed_count + VALUES(failed_count),
                input_tokens = input_tokens + VALUES(input_tokens),
                output_tokens = output_tokens + VALUES(output_tokens),
                reasoning_tokens = reasoning_tokens + VALUES(reasoning_tokens),
                cache_read_tokens = cache_read_tokens + VALUES(cache_read_tokens),
                cache_write_tokens = cache_write_tokens + VALUES(cache_write_tokens),
                billed_quota = billed_quota + VALUES(billed_quota)
            """)
    int addUsage(
            @Param("usage") UsageRecordEntity usage,
            @Param("status") String status,
            @Param("summaryDate") LocalDate summaryDate
    );

    @Select("""
            SELECT
                COALESCE(SUM(request_count), 0) AS request_count,
                COALESCE(SUM(completed_count), 0) AS completed_count,
                COALESCE(SUM(pending_count), 0) AS pending_count,
                COALESCE(SUM(failed_count), 0) AS failed_count,
                COALESCE(SUM(input_tokens), 0) AS input_tokens,
                COALESCE(SUM(output_tokens), 0) AS output_tokens,
                COALESCE(SUM(reasoning_tokens), 0) AS reasoning_tokens,
                COALESCE(SUM(cache_read_tokens), 0) AS cache_read_tokens,
                COALESCE(SUM(cache_write_tokens), 0) AS cache_write_tokens,
                COALESCE(SUM(billed_quota), 0) AS billed_quota
            FROM billing_usage_daily_summary
            WHERE user_id = #{userId}
              AND summary_date >= #{startsOn}
              AND summary_date < #{endsOnExclusive}
            """)
    UsageAggregate aggregate(
            @Param("userId") Long userId,
            @Param("startsOn") LocalDate startsOn,
            @Param("endsOnExclusive") LocalDate endsOnExclusive
    );

    @Select("""
            SELECT
                summary_date,
                request_count,
                input_tokens,
                output_tokens,
                reasoning_tokens,
                cache_read_tokens,
                cache_write_tokens,
                billed_quota
            FROM billing_usage_daily_summary
            WHERE user_id = #{userId}
              AND summary_date >= #{startsOn}
              AND summary_date < #{endsOnExclusive}
            ORDER BY summary_date
            """)
    List<DailyUsageAggregate> listDaily(
            @Param("userId") Long userId,
            @Param("startsOn") LocalDate startsOn,
            @Param("endsOnExclusive") LocalDate endsOnExclusive
    );
}
