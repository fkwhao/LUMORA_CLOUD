package com.lumora.cloud.billing.web;

import com.lumora.cloud.billing.security.BillingAccess;
import com.lumora.cloud.billing.service.BillingCatalogService;
import com.lumora.cloud.billing.service.BillingHistoryService;
import com.lumora.cloud.billing.service.SubscriptionService;
import com.lumora.cloud.billing.service.PurchaseOrderService;
import com.lumora.cloud.billing.web.BillingWebContracts.BillingHistoryResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.BillingOverviewResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.PlanResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.CreatePurchaseOrderRequest;
import com.lumora.cloud.billing.web.BillingWebContracts.PaymentCapabilitiesResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.PurchaseOrderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/app/billing")
public class BillingAppController {

    private final BillingAccess access;
    private final BillingCatalogService catalogService;
    private final SubscriptionService subscriptionService;
    private final BillingHistoryService historyService;
    private final PurchaseOrderService orderService;

    public BillingAppController(
            BillingAccess access,
            BillingCatalogService catalogService,
            SubscriptionService subscriptionService,
            BillingHistoryService historyService,
            PurchaseOrderService orderService
    ) {
        this.access = access;
        this.catalogService = catalogService;
        this.subscriptionService = subscriptionService;
        this.historyService = historyService;
        this.orderService = orderService;
    }

    @GetMapping("/plans")
    public List<PlanResponse> plans() {
        access.requireUserId();
        return catalogService.listPublished();
    }

    @GetMapping("/overview")
    public BillingOverviewResponse overview() {
        return subscriptionService.overview(access.requireUserId());
    }

    @GetMapping("/history")
    public BillingHistoryResponse history() {
        return historyService.recent(access.requireUserId());
    }

    @GetMapping("/payment-capabilities")
    public PaymentCapabilitiesResponse paymentCapabilities() {
        access.requireUserId();
        return orderService.capabilities();
    }

    @GetMapping("/orders")
    public List<PurchaseOrderResponse> orders() {
        return orderService.list(access.requireUserId());
    }

    @GetMapping("/orders/{orderNo}")
    public PurchaseOrderResponse order(
            @PathVariable @Pattern(regexp = "LU[A-Z0-9]{20,38}") String orderNo
    ) {
        return orderService.get(access.requireUserId(), orderNo);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderResponse createOrder(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody CreatePurchaseOrderRequest request
    ) {
        return orderService.create(access.requireUserId(), idempotencyKey, request);
    }

    @PostMapping("/orders/{orderNo}/payments/mock")
    public PurchaseOrderResponse mockPay(
            @PathVariable @Pattern(regexp = "LU[A-Z0-9]{20,38}") String orderNo
    ) {
        return orderService.mockPay(access.requireUserId(), orderNo);
    }

    @PostMapping("/orders/{orderNo}/cancel")
    public PurchaseOrderResponse cancelOrder(
            @PathVariable @Pattern(regexp = "LU[A-Z0-9]{20,38}") String orderNo
    ) {
        return orderService.cancel(access.requireUserId(), orderNo);
    }
}
