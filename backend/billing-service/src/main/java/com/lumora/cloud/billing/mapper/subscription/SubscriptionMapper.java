package com.lumora.cloud.billing.mapper.subscription;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.billing.domain.entity.subscription.SubscriptionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

public interface SubscriptionMapper extends BaseMapper<SubscriptionEntity> {

    @Select("""
            SELECT * FROM billing_subscription
            WHERE user_id = #{userId}
              AND status = 'ACTIVE'
              AND starts_at < #{endsAt}
              AND ends_at > #{startsAt}
            ORDER BY starts_at
            LIMIT 1
            FOR UPDATE
            """)
    SubscriptionEntity findOverlappingForUpdate(
            @Param("userId") Long userId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt
    );

    @Select("""
            SELECT * FROM billing_subscription
            WHERE source = #{source} AND source_reference = #{sourceReference}
            LIMIT 1
            FOR UPDATE
            """)
    SubscriptionEntity findBySourceReferenceForUpdate(
            @Param("source") String source,
            @Param("sourceReference") String sourceReference
    );

    @Select("""
            SELECT * FROM billing_subscription
            WHERE user_id = #{userId}
              AND status = 'ACTIVE'
              AND starts_at <= #{now}
              AND ends_at > #{now}
            ORDER BY ends_at DESC
            LIMIT 1
            FOR UPDATE
            """)
    SubscriptionEntity findActiveForUpdate(@Param("userId") Long userId, @Param("now") Instant now);

    @Select("""
            SELECT * FROM billing_subscription
            WHERE user_id = #{userId}
              AND status = 'ACTIVE'
              AND starts_at <= #{now}
              AND ends_at > #{now}
            ORDER BY ends_at DESC
            LIMIT 1
            """)
    SubscriptionEntity findActive(@Param("userId") Long userId, @Param("now") Instant now);

    @Select("""
            SELECT * FROM billing_subscription
            WHERE user_id = #{userId}
              AND status = 'ACTIVE'
              AND ends_at > #{now}
            ORDER BY ends_at DESC
            LIMIT 1
            FOR UPDATE
            """)
    SubscriptionEntity findLatestEndingForUpdate(@Param("userId") Long userId, @Param("now") Instant now);

    @Select("""
            SELECT COUNT(*) FROM billing_subscription
            WHERE status = 'ACTIVE' AND starts_at <= #{now} AND ends_at > #{now}
            """)
    long countActiveAt(@Param("now") Instant now);
}
