package com.lumora.cloud.billing.domain.vo.reconciliation;
public record ReconciliationBatchResult(String requestId, boolean completed, String status, String code, String message) {}
