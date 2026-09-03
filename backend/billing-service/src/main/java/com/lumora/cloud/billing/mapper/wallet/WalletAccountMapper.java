package com.lumora.cloud.billing.mapper.wallet;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.billing.domain.entity.wallet.WalletAccountEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface WalletAccountMapper extends BaseMapper<WalletAccountEntity> {

    @Insert("""
            INSERT INTO wallet_account (user_id, currency)
            VALUES (#{userId}, #{currency})
            ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)
            """)
    int ensureExists(@Param("userId") Long userId, @Param("currency") String currency);

    @Select("SELECT * FROM wallet_account WHERE user_id = #{userId} AND currency = #{currency} LIMIT 1 FOR UPDATE")
    WalletAccountEntity findForUpdate(@Param("userId") Long userId, @Param("currency") String currency);

    @Select("SELECT * FROM wallet_account WHERE user_id = #{userId} ORDER BY currency")
    List<WalletAccountEntity> findByUserId(@Param("userId") Long userId);

    @Update("""
            UPDATE wallet_account
            SET available_minor = available_minor + #{amount}, version = version + 1
            WHERE id = #{id}
            """)
    int credit(@Param("id") Long id, @Param("amount") long amount);

    @Update("""
            UPDATE wallet_account
            SET available_minor = available_minor - #{amount}, version = version + 1
            WHERE id = #{id} AND available_minor >= #{amount}
            """)
    int debit(@Param("id") Long id, @Param("amount") long amount);
}
