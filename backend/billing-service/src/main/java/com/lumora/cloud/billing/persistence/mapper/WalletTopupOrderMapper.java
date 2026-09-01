package com.lumora.cloud.billing.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.billing.persistence.entity.WalletTopupOrderEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

public interface WalletTopupOrderMapper extends BaseMapper<WalletTopupOrderEntity> {

    @Insert("""
            INSERT IGNORE INTO wallet_topup_order (
                id, order_no, account_id, user_id, amount_minor, currency,
                status, idempotency_key, expires_at
            ) VALUES (
                #{entity.id}, #{entity.orderNo}, #{entity.accountId}, #{entity.userId},
                #{entity.amountMinor}, #{entity.currency}, #{entity.status},
                #{entity.idempotencyKey}, #{entity.expiresAt}
            )
            """)
    int insertPendingIgnore(@Param("entity") WalletTopupOrderEntity entity);

    @Select("""
            SELECT * FROM wallet_topup_order
            WHERE user_id = #{userId} AND idempotency_key = #{idempotencyKey}
            LIMIT 1 FOR UPDATE
            """)
    WalletTopupOrderEntity findByIdempotencyForUpdate(
            @Param("userId") Long userId, @Param("idempotencyKey") String idempotencyKey
    );

    @Select("SELECT * FROM wallet_topup_order WHERE order_no = #{orderNo} LIMIT 1 FOR UPDATE")
    WalletTopupOrderEntity findByOrderNoForUpdate(@Param("orderNo") String orderNo);

    @Update("""
            UPDATE wallet_topup_order SET status = 'PAID', paid_at = #{paidAt}
            WHERE id = #{id} AND status = 'PENDING_PAYMENT'
            """)
    int markPaid(@Param("id") String id, @Param("paidAt") Instant paidAt);

    @Update("UPDATE wallet_topup_order SET status = 'CANCELED' WHERE id = #{id} AND status = 'PENDING_PAYMENT'")
    int markCanceled(@Param("id") String id);

    @Update("""
            UPDATE wallet_topup_order SET status = 'EXPIRED'
            WHERE order_no = #{orderNo} AND status = 'PENDING_PAYMENT' AND expires_at <= #{now}
            """)
    int expirePendingOrder(@Param("orderNo") String orderNo, @Param("now") Instant now);

    @Update("""
            UPDATE wallet_topup_order SET status = 'EXPIRED'
            WHERE user_id = #{userId} AND status = 'PENDING_PAYMENT' AND expires_at <= #{now}
            """)
    int expireUserOrders(@Param("userId") Long userId, @Param("now") Instant now);

    @Update("""
            UPDATE wallet_topup_order SET status = 'EXPIRED'
            WHERE status = 'PENDING_PAYMENT' AND expires_at <= #{now}
            LIMIT #{batchSize}
            """)
    int expirePending(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
