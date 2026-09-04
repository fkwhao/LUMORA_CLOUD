package com.lumora.cloud.billing.controller.admin;

import com.lumora.cloud.billing.security.BillingAccess;
import com.lumora.cloud.billing.service.IBillingCatalogService;
import com.lumora.cloud.billing.service.IBillingStatisticsService;
import com.lumora.cloud.billing.service.IPurchaseOrderService;
import com.lumora.cloud.billing.service.ISubscriptionService;
import com.lumora.cloud.billing.service.IWalletService;
import com.lumora.cloud.billing.domain.vo.statistics.AdminBillingStatisticsResponse;
import com.lumora.cloud.billing.domain.dto.plan.CreatePlanRequest;
import com.lumora.cloud.billing.domain.dto.plan.CreatePlanVersionRequest;
import com.lumora.cloud.billing.domain.dto.subscription.GrantSubscriptionRequest;
import com.lumora.cloud.billing.domain.vo.plan.PlanResponse;
import com.lumora.cloud.billing.domain.vo.subscription.SubscriptionResponse;
import com.lumora.cloud.billing.domain.vo.order.PurchaseOrderResponse;
import com.lumora.cloud.billing.domain.dto.wallet.AdminWalletAdjustmentRequest;
import com.lumora.cloud.billing.domain.vo.wallet.WalletAdjustmentResponse;
import com.lumora.cloud.billing.domain.vo.wallet.WalletOverviewResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/admin/billing")
@RequiredArgsConstructor
public class BillingAdminController {

    private final BillingAccess access;
    private final IBillingCatalogService catalogService;
    private final ISubscriptionService subscriptionService;
    private final IPurchaseOrderService orderService;
    private final IBillingStatisticsService statisticsService;
    private final IWalletService walletService;

    @GetMapping("/plans")
    public List<PlanResponse> plans() {
        access.requireAdmin();
        return catalogService.listPublished();
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse createPlan(@Valid @RequestBody CreatePlanRequest request) {
        access.requireAdmin();
        return catalogService.create(request);
    }

    @GetMapping("/plans/{planId}/versions")
    public List<PlanResponse> planVersions(@PathVariable Long planId) {
        access.requireAdmin();
        return catalogService.versions(planId);
    }

    @PostMapping("/plans/{planId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse publishPlanVersion(
            @PathVariable Long planId,
            @Valid @RequestBody CreatePlanVersionRequest request
    ) {
        access.requireAdmin();
        return catalogService.publishVersion(planId, request);
    }

    @GetMapping("/subscriptions")
    public List<SubscriptionResponse> subscriptions(@RequestParam(required = false) Long userId) {
        access.requireAdmin();
        return subscriptionService.listRecent(userId);
    }

    @GetMapping("/orders")
    public List<PurchaseOrderResponse> orders() {
        access.requireAdmin();
        return orderService.listRecentForAdmin();
    }

    @GetMapping("/statistics")
    public AdminBillingStatisticsResponse statistics() {
        access.requireAdmin();
        return statisticsService.statistics();
    }

    @GetMapping("/wallets/{userId}")
    public WalletOverviewResponse wallet(@PathVariable Long userId) {
        access.requireAdmin();
        return walletService.adminOverview(userId);
    }

    @PostMapping("/wallets/adjustments")
    public WalletAdjustmentResponse adjustWallet(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody AdminWalletAdjustmentRequest request
    ) {
        return walletService.adjust(access.requireAdminUserId(), idempotencyKey, request);
    }

    @PostMapping("/subscriptions/grant")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse grant(@Valid @RequestBody GrantSubscriptionRequest request) {
        access.requireAdmin();
        return subscriptionService.grant(request);
    }
}
