package com.lumora.cloud.billing.mapper.quota;

import com.lumora.cloud.billing.domain.entity.quota.QuotaLedgerEntity;
import com.lumora.cloud.billing.domain.projection.history.QuotaLedgerAggregate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

public interface QuotaDailySummaryMapper {

    @Insert("""
            INSERT INTO billing_quota_daily_summary (
                user_id, summary_date, entry_count, granted_delta, reserved_delta, consumed_delta
            ) VALUES (
                #{entry.userId}, #{summaryDate}, 1,
                #{entry.grantedDelta}, #{entry.reservedDelta}, #{entry.consumedDelta}
            )
            ON DUPLICATE KEY UPDATE
                entry_count = entry_count + 1,
                granted_delta = granted_delta + VALUES(granted_delta),
                reserved_delta = reserved_delta + VALUES(reserved_delta),
                consumed_delta = consumed_delta + VALUES(consumed_delta)
            """)
    int addEntry(
            @Param("entry") QuotaLedgerEntity entry,
            @Param("summaryDate") LocalDate summaryDate
    );

    @Select("""
            SELECT
                COALESCE(SUM(entry_count), 0) AS entry_count,
                COALESCE(SUM(granted_delta), 0) AS granted_delta,
                COALESCE(SUM(reserved_delta), 0) AS reserved_delta,
                COALESCE(SUM(consumed_delta), 0) AS consumed_delta
            FROM billing_quota_daily_summary
            WHERE user_id = #{userId}
              AND summary_date >= #{startsOn}
              AND summary_date < #{endsOnExclusive}
            """)
    QuotaLedgerAggregate aggregate(
            @Param("userId") Long userId,
            @Param("startsOn") LocalDate startsOn,
            @Param("endsOnExclusive") LocalDate endsOnExclusive
    );
}
