package com.lumora.cloud.api.billing;

import com.lumora.cloud.api.billing.BillingContracts.PendingRequest;
import com.lumora.cloud.api.billing.BillingContracts.ReleaseRequest;
import com.lumora.cloud.api.billing.BillingContracts.ReservationResponse;
import com.lumora.cloud.api.billing.BillingContracts.ReserveRequest;
import com.lumora.cloud.api.billing.BillingContracts.SettleRequest;
import com.lumora.cloud.api.billing.BillingContracts.SettlementResponse;
import com.lumora.cloud.api.fallback.BillingClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "lumora-billing-service",
        path = "/internal/billing",
        fallbackFactory = BillingClientFallbackFactory.class
)
public interface BillingClient {

    @PostMapping("/reservations")
    ReservationResponse reserve(@RequestBody ReserveRequest request);

    @PostMapping("/reservations/{requestId}/settle")
    SettlementResponse settle(@PathVariable("requestId") String requestId, @RequestBody SettleRequest request);

    @PostMapping("/reservations/{requestId}/release")
    ReservationResponse release(@PathVariable("requestId") String requestId, @RequestBody ReleaseRequest request);

    @PostMapping("/reservations/{requestId}/pending")
    ReservationResponse markPending(@PathVariable("requestId") String requestId, @RequestBody PendingRequest request);
}
