package com.lumora.cloud.billing.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lumora.cloud.billing.persistence.entity.QuotaLedgerEntity;
import com.lumora.cloud.billing.persistence.entity.UsageRecordEntity;
import com.lumora.cloud.billing.persistence.mapper.QuotaLedgerMapper;
import com.lumora.cloud.billing.persistence.mapper.UsageRecordMapper;
import com.lumora.cloud.billing.web.BillingWebContracts.BillingHistoryResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.LedgerEntryResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.UsageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingHistoryService {

    private final QuotaLedgerMapper ledgerMapper;
    private final UsageRecordMapper usageMapper;

    public BillingHistoryService(QuotaLedgerMapper ledgerMapper, UsageRecordMapper usageMapper) {
        this.ledgerMapper = ledgerMapper;
        this.usageMapper = usageMapper;
    }

    @Transactional(readOnly = true)
    public BillingHistoryResponse recent(Long userId) {
        var ledger = ledgerMapper.selectList(Wrappers.<QuotaLedgerEntity>lambdaQuery()
                        .eq(QuotaLedgerEntity::getUserId, userId)
                        .orderByDesc(QuotaLedgerEntity::getCreatedAt)
                        .last("LIMIT 100"))
                .stream()
                .map(entry -> new LedgerEntryResponse(
                        entry.getId(), entry.getEntryType(), entry.getReferenceType(), entry.getReferenceId(),
                        entry.getGrantedDelta(), entry.getReservedDelta(), entry.getConsumedDelta(),
                        entry.getDescription(), entry.getCreatedAt()
                ))
                .toList();
        var usage = usageMapper.selectList(Wrappers.<UsageRecordEntity>lambdaQuery()
                        .eq(UsageRecordEntity::getUserId, userId)
                        .orderByDesc(UsageRecordEntity::getOccurredAt)
                        .last("LIMIT 100"))
                .stream()
                .map(record -> new UsageResponse(
                        record.getUsageId(), record.getRequestId(), record.getModelCode(), record.getPricingVersion(),
                        record.getInputTokens(), record.getOutputTokens(), record.getReasoningTokens(),
                        record.getCacheReadTokens(), record.getCacheWriteTokens(), record.getBilledQuota(),
                        record.getStatus(), record.getOccurredAt()
                ))
                .toList();
        return new BillingHistoryResponse(ledger, usage);
    }
}
