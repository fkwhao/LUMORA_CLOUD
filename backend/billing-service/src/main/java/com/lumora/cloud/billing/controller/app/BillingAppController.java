package com.lumora.cloud.billing.controller.app;

import com.lumora.cloud.billing.security.BillingAccess;
import com.lumora.cloud.billing.domain.enums.BillingHistoryScope;
import com.lumora.cloud.billing.domain.enums.UsageChartRange;
import com.lumora.cloud.billing.service.IBillingCatalogService;
import com.lumora.cloud.billing.service.IBillingHistoryService;
import com.lumora.cloud.billing.service.IPurchaseOrderService;
import com.lumora.cloud.billing.service.ISubscriptionService;
import com.lumora.cloud.billing.service.IWalletService;
import com.lumora.cloud.billing.domain.vo.history.BillingHistoryResponse;
import com.lumora.cloud.billing.domain.vo.history.UsageChartResponse;
import com.lumora.cloud.billing.domain.vo.overview.BillingOverviewResponse;
import com.lumora.cloud.billing.domain.vo.plan.PlanResponse;
import com.lumora.cloud.billing.domain.dto.order.CreatePurchaseOrderRequest;
import com.lumora.cloud.billing.domain.vo.order.PaymentCapabilitiesResponse;
import com.lumora.cloud.billing.domain.vo.order.PurchaseOrderResponse;
import com.lumora.cloud.billing.domain.dto.wallet.CreateWalletTopupRequest;
import com.lumora.cloud.billing.domain.vo.wallet.WalletOverviewResponse;
import com.lumora.cloud.billing.domain.vo.wallet.WalletTopupOrderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/app/billing")
@RequiredArgsConstructor
public class BillingAppController {

    private final BillingAccess access;
    private final IBillingCatalogService catalogService;
    private final ISubscriptionService subscriptionService;
    private final IBillingHistoryService historyService;
    private final IPurchaseOrderService orderService;
    private final IWalletService walletService;

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
    public BillingHistoryResponse history(
            @RequestParam(defaultValue = "CURRENT_PERIOD") BillingHistoryScope scope
    ) {
        return historyService.recent(access.requireUserId(), scope);
    }

    @GetMapping("/usage-chart")
    public UsageChartResponse usageChart(
            @RequestParam(defaultValue = "WEEK") UsageChartRange range,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchor
    ) {
        return historyService.usageChart(access.requireUserId(), range, anchor);
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

    @PostMapping("/orders/{orderNo}/payments/wallet")
    public PurchaseOrderResponse walletPay(
            @PathVariable @Pattern(regexp = "LU[A-Z0-9]{20,38}") String orderNo
    ) {
        return orderService.walletPay(access.requireUserId(), orderNo);
    }

    @GetMapping("/wallet")
    public WalletOverviewResponse wallet() {
        return walletService.overview(access.requireUserId());
    }

    @PostMapping("/wallet/topups")
    @ResponseStatus(HttpStatus.CREATED)
    public WalletTopupOrderResponse createTopup(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody CreateWalletTopupRequest request
    ) {
        return walletService.createTopup(access.requireUserId(), idempotencyKey, request);
    }

    @PostMapping("/wallet/topups/{orderNo}/payments/mock")
    public WalletTopupOrderResponse mockPayTopup(
            @PathVariable @Pattern(regexp = "WU[A-Z0-9]{20,38}") String orderNo
    ) {
        return walletService.mockPayTopup(access.requireUserId(), orderNo);
    }

    @PostMapping("/wallet/topups/{orderNo}/cancel")
    public WalletTopupOrderResponse cancelTopup(
            @PathVariable @Pattern(regexp = "WU[A-Z0-9]{20,38}") String orderNo
    ) {
        return walletService.cancelTopup(access.requireUserId(), orderNo);
    }

    @PostMapping("/orders/{orderNo}/cancel")
    public PurchaseOrderResponse cancelOrder(
            @PathVariable @Pattern(regexp = "LU[A-Z0-9]{20,38}") String orderNo
    ) {
        return orderService.cancel(access.requireUserId(), orderNo);
    }
}
