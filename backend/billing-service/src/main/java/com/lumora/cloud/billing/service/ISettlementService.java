package com.lumora.cloud.billing.service;

import com.lumora.cloud.api.billing.BillingContracts.PendingRequest;
import com.lumora.cloud.api.billing.BillingContracts.ReleaseRequest;
import com.lumora.cloud.api.billing.BillingContracts.ReservationResponse;
import com.lumora.cloud.api.billing.BillingContracts.ReserveRequest;
import com.lumora.cloud.api.billing.BillingContracts.SettleRequest;
import com.lumora.cloud.api.billing.BillingContracts.SettlementResponse;
import com.lumora.cloud.billing.domain.dto.reconciliation.ReconciliationRequest;
import com.lumora.cloud.billing.domain.vo.reconciliation.ReconciliationCase;

public interface ISettlementService {

    ReconciliationCase reconciliation(String requestId);

    ReconciliationCase reconcile(Long actorUserId, String requestId, ReconciliationRequest request);

    com.lumora.cloud.billing.domain.vo.reconciliation.ReconciliationPage listReconciliation(
            String status, String requestId, Long userId, int page, int pageSize);

    ReconciliationCase autoReconcile(String requestId);

    void recordReconciliationFailure(String requestId, String reason);

    void expire(String requestId);

    ReservationResponse reserve(ReserveRequest request);

    SettlementResponse settle(String requestId, SettleRequest request);

    ReservationResponse release(String requestId, ReleaseRequest request);

    ReservationResponse markPending(String requestId, PendingRequest request);
}
