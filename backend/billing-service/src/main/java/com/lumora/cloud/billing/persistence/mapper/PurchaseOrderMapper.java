package com.lumora.cloud.billing.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.billing.persistence.entity.PurchaseOrderEntity;
import com.lumora.cloud.billing.persistence.projection.RevenueAggregate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrderEntity> {

    @Insert("""
            INSERT IGNORE INTO billing_purchase_order (
                id, order_no, user_id, plan_version_id, plan_code, plan_name,
                amount_minor, currency, status, idempotency_key, expires_at
            ) VALUES (
                #{entity.id}, #{entity.orderNo}, #{entity.userId}, #{entity.planVersionId},
                #{entity.planCode}, #{entity.planName}, #{entity.amountMinor}, #{entity.currency},
                #{entity.status}, #{entity.idempotencyKey}, #{entity.expiresAt}
            )
            """)
    int insertPendingIgnore(@Param("entity") PurchaseOrderEntity entity);

    @Select("""
            SELECT * FROM billing_purchase_order
            WHERE user_id = #{userId} AND idempotency_key = #{idempotencyKey}
            LIMIT 1 FOR UPDATE
            """)
    PurchaseOrderEntity findByIdempotencyForUpdate(
            @Param("userId") Long userId,
            @Param("idempotencyKey") String idempotencyKey
    );

    @Select("SELECT * FROM billing_purchase_order WHERE order_no = #{orderNo} LIMIT 1 FOR UPDATE")
    PurchaseOrderEntity findByOrderNoForUpdate(@Param("orderNo") String orderNo);

    @Select("SELECT * FROM billing_purchase_order WHERE user_id = #{userId} AND order_no = #{orderNo} LIMIT 1")
    PurchaseOrderEntity findByUserAndOrderNo(@Param("userId") Long userId, @Param("orderNo") String orderNo);

    @Update("""
            UPDATE billing_purchase_order
            SET status = 'FULFILLED', paid_at = #{paidAt}, fulfilled_at = #{paidAt},
                subscription_id = #{subscriptionId}
            WHERE id = #{id} AND status = 'PENDING_PAYMENT'
            """)
    int markFulfilled(
            @Param("id") String id,
            @Param("paidAt") Instant paidAt,
            @Param("subscriptionId") String subscriptionId
    );

    @Update("""
            UPDATE billing_purchase_order
            SET status = 'CANCELED'
            WHERE id = #{id} AND status = 'PENDING_PAYMENT'
            """)
    int markCanceled(@Param("id") String id);

    @Update("""
            UPDATE billing_purchase_order
            SET status = 'EXPIRED'
            WHERE id = #{id} AND status = 'PENDING_PAYMENT'
            """)
    int markExpired(@Param("id") String id);

    @Update("""
            UPDATE billing_purchase_order
            SET status = 'EXPIRED'
            WHERE user_id = #{userId} AND status = 'PENDING_PAYMENT' AND expires_at <= #{now}
            """)
    int expireUserOrders(@Param("userId") Long userId, @Param("now") Instant now);

    @Update("""
            UPDATE billing_purchase_order
            SET status = 'EXPIRED'
            WHERE status = 'PENDING_PAYMENT' AND expires_at <= #{now}
            LIMIT #{batchSize}
            """)
    int expirePending(@Param("now") Instant now, @Param("batchSize") int batchSize);

    @Select("""
            SELECT COUNT(*) FROM billing_purchase_order
            WHERE status = 'PENDING_PAYMENT' AND expires_at > #{now}
            """)
    long countPayableAt(@Param("now") Instant now);

    @Select("""
            SELECT currency, SUM(amount_minor) AS amount_minor, COUNT(*) AS order_count
            FROM billing_purchase_order
            WHERE status = 'FULFILLED' AND paid_at >= #{startsAt} AND paid_at < #{endsAt}
            GROUP BY currency
            ORDER BY currency
            """)
    List<RevenueAggregate> revenueBetween(
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt
    );
}
