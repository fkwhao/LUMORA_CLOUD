package com.lumora.cloud.billing.service;

import com.lumora.cloud.api.billing.BillingContracts.PendingRequest;
import com.lumora.cloud.api.billing.BillingContracts.ReleaseRequest;
import com.lumora.cloud.api.billing.BillingContracts.ReservationResponse;
import com.lumora.cloud.api.billing.BillingContracts.ReserveRequest;
import com.lumora.cloud.api.billing.BillingContracts.SettleRequest;
import com.lumora.cloud.api.billing.BillingContracts.SettlementResponse;

public interface ISettlementService {

    ReservationResponse reserve(ReserveRequest request);

    SettlementResponse settle(String requestId, SettleRequest request);

    ReservationResponse release(String requestId, ReleaseRequest request);

    ReservationResponse markPending(String requestId, PendingRequest request);
}
