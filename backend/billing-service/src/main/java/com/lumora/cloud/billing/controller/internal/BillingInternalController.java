package com.lumora.cloud.billing.controller.internal;

import com.lumora.cloud.api.billing.BillingContracts.PendingRequest;
import com.lumora.cloud.api.billing.BillingContracts.ReleaseRequest;
import com.lumora.cloud.api.billing.BillingContracts.ReservationResponse;
import com.lumora.cloud.api.billing.BillingContracts.ReserveRequest;
import com.lumora.cloud.api.billing.BillingContracts.SettleRequest;
import com.lumora.cloud.api.billing.BillingContracts.SettlementResponse;
import com.lumora.cloud.billing.security.InternalRequestAuthorizer;
import com.lumora.cloud.billing.service.ISettlementService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/billing")
@RequiredArgsConstructor
public class BillingInternalController {

    private final InternalRequestAuthorizer authorizer;
    private final ISettlementService settlementService;

    @PostMapping("/reservations")
    public ReservationResponse reserve(HttpServletRequest servletRequest, @RequestBody ReserveRequest request) {
        authorizer.requireModelGateway(servletRequest);
        return settlementService.reserve(request);
    }

    @PostMapping("/reservations/{requestId}/settle")
    public SettlementResponse settle(
            HttpServletRequest servletRequest,
            @PathVariable String requestId,
            @RequestBody SettleRequest request
    ) {
        authorizer.requireModelGateway(servletRequest);
        return settlementService.settle(requestId, request);
    }

    @PostMapping("/reservations/{requestId}/release")
    public ReservationResponse release(
            HttpServletRequest servletRequest,
            @PathVariable String requestId,
            @RequestBody(required = false) ReleaseRequest request
    ) {
        authorizer.requireModelGateway(servletRequest);
        return settlementService.release(requestId, request);
    }

    @PostMapping("/reservations/{requestId}/pending")
    public ReservationResponse markPending(
            HttpServletRequest servletRequest,
            @PathVariable String requestId,
            @RequestBody(required = false) PendingRequest request
    ) {
        authorizer.requireModelGateway(servletRequest);
        return settlementService.markPending(requestId, request);
    }
}
