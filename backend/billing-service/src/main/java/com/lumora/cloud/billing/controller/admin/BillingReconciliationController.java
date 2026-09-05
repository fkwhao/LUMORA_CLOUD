package com.lumora.cloud.billing.controller.admin;

import com.lumora.cloud.billing.domain.dto.reconciliation.ReconciliationRequest;
import com.lumora.cloud.billing.domain.vo.reconciliation.ReconciliationCase;
import com.lumora.cloud.billing.security.BillingAccess;
import com.lumora.cloud.billing.service.ISettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/billing/reconciliation")
@RequiredArgsConstructor
public class BillingReconciliationController {
    private final BillingAccess access;
    private final ISettlementService settlements;

    @GetMapping
    public com.lumora.cloud.billing.domain.vo.reconciliation.ReconciliationPage list(
            @RequestParam(defaultValue = "PENDING_RECONCILIATION") String status,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        access.requireAdmin();
        return settlements.listReconciliation("ALL".equals(status) ? null : status, requestId, userId, page, pageSize);
    }

    @PostMapping("/batch")
    public java.util.List<com.lumora.cloud.billing.domain.vo.reconciliation.ReconciliationBatchResult> batch(
            @Valid @RequestBody com.lumora.cloud.billing.domain.dto.reconciliation.ReconciliationBatchRequest request) {
        Long actor = access.requireAdminUserId();
        var results = new java.util.ArrayList<com.lumora.cloud.billing.domain.vo.reconciliation.ReconciliationBatchResult>();
        // Each call uses its own transaction; a failed item cannot roll back successful items.
        for (String id : new java.util.LinkedHashSet<>(request.requestIds())) {
            try {
                var evidence = settlements.reconciliation(id);
                var usage = evidence.usage();
                com.lumora.cloud.api.billing.BillingContracts.SettleRequest settlement = null;
                if (request.action() == ReconciliationRequest.Action.SETTLE) {
                    if (usage == null) throw new com.lumora.cloud.billing.error.ApiException(
                            org.springframework.http.HttpStatus.CONFLICT, "EVIDENCE_REQUIRED", "缺少用量依据，请先单独核对");
                    settlement = new com.lumora.cloud.api.billing.BillingContracts.SettleRequest(
                            usage.getUsageId(), usage.getPricingVersion(), usage.getInputTokens(), usage.getOutputTokens(),
                            usage.getReasoningTokens(), usage.getCacheReadTokens(), usage.getCacheWriteTokens(),
                            usage.getBilledQuota(), usage.getOccurredAt());
                }
                var resolved = settlements.reconcile(actor, id,
                        new ReconciliationRequest(request.action(), request.reason(), settlement));
                results.add(new com.lumora.cloud.billing.domain.vo.reconciliation.ReconciliationBatchResult(
                        id, true, resolved.reservation().getStatus(), null, "处理完成"));
            } catch (com.lumora.cloud.billing.error.ApiException error) {
                results.add(new com.lumora.cloud.billing.domain.vo.reconciliation.ReconciliationBatchResult(
                        id, false, null, error.getCode(), error.getMessage()));
            } catch (RuntimeException error) {
                results.add(new com.lumora.cloud.billing.domain.vo.reconciliation.ReconciliationBatchResult(
                        id, false, null, "RECONCILIATION_FAILED", "处理暂时失败，请刷新核对后重试"));
            }
        }
        return results;
    }

    @GetMapping("/{requestId}")
    public ReconciliationCase get(@PathVariable String requestId) {
        access.requireAdmin();
        return settlements.reconciliation(requestId);
    }

    @PostMapping("/{requestId}")
    public ReconciliationCase resolve(
            @PathVariable String requestId,
            @Valid @RequestBody ReconciliationRequest request
    ) {
        return settlements.reconcile(access.requireAdminUserId(), requestId, request);
    }
}
