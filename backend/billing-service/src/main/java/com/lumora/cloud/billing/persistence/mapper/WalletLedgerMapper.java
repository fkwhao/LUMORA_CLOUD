package com.lumora.cloud.billing.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.billing.persistence.entity.WalletLedgerEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WalletLedgerMapper extends BaseMapper<WalletLedgerEntity> {

    @Select("""
            SELECT * FROM wallet_ledger
            WHERE entry_type = #{entryType} AND reference_type = #{referenceType} AND reference_id = #{referenceId}
            LIMIT 1
            """)
    WalletLedgerEntity findByReference(
            @Param("entryType") String entryType,
            @Param("referenceType") String referenceType,
            @Param("referenceId") String referenceId
    );
}
