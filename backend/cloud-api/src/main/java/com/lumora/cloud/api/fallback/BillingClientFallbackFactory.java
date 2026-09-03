package com.lumora.cloud.api.fallback;

import com.lumora.cloud.api.billing.BillingClient;
import com.lumora.cloud.api.billing.BillingContracts.PendingRequest;
import com.lumora.cloud.api.billing.BillingContracts.ReleaseRequest;
import com.lumora.cloud.api.billing.BillingContracts.ReservationResponse;
import com.lumora.cloud.api.billing.BillingContracts.ReserveRequest;
import com.lumora.cloud.api.billing.BillingContracts.SettleRequest;
import com.lumora.cloud.api.billing.BillingContracts.SettlementResponse;
import org.springframework.cloud.openfeign.FallbackFactory;

public class BillingClientFallbackFactory implements FallbackFactory<BillingClient> {

    private static final String SERVICE_NAME = "lumora-billing-service";

    @Override
    public BillingClient create(Throwable cause) {
        return new BillingClient() {
            @Override
            public ReservationResponse reserve(ReserveRequest request) {
                throw failure("reserve", cause);
            }

            @Override
            public SettlementResponse settle(String requestId, SettleRequest request) {
                throw failure("settle", cause);
            }

            @Override
            public ReservationResponse release(String requestId, ReleaseRequest request) {
                throw failure("release", cause);
            }

            @Override
            public ReservationResponse markPending(String requestId, PendingRequest request) {
                throw failure("markPending", cause);
            }
        };
    }

    private RuntimeException failure(String operation, Throwable cause) {
        return RemoteServiceFallbacks.failure(SERVICE_NAME, operation, cause);
    }
}
