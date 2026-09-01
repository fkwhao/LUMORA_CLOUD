package com.lumora.cloud.billing.web;

import com.lumora.cloud.billing.security.BillingAccess;
import com.lumora.cloud.billing.service.BillingCatalogService;
import com.lumora.cloud.billing.service.SubscriptionService;
import com.lumora.cloud.billing.service.PurchaseOrderService;
import com.lumora.cloud.billing.service.BillingStatisticsService;
import com.lumora.cloud.billing.service.WalletService;
import com.lumora.cloud.billing.web.BillingWebContracts.AdminBillingStatisticsResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.CreatePlanRequest;
import com.lumora.cloud.billing.web.BillingWebContracts.CreatePlanVersionRequest;
import com.lumora.cloud.billing.web.BillingWebContracts.GrantSubscriptionRequest;
import com.lumora.cloud.billing.web.BillingWebContracts.PlanResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.SubscriptionResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.PurchaseOrderResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.AdminWalletAdjustmentRequest;
import com.lumora.cloud.billing.web.BillingWebContracts.WalletAdjustmentResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.WalletOverviewResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class BillingAdminController {

    private final BillingAccess access;
    private final BillingCatalogService catalogService;
    private final SubscriptionService subscriptionService;
    private final PurchaseOrderService orderService;
    private final BillingStatisticsService statisticsService;
    private final WalletService walletService;

    public BillingAdminController(
            BillingAccess access,
            BillingCatalogService catalogService,
            SubscriptionService subscriptionService,
            PurchaseOrderService orderService,
            BillingStatisticsService statisticsService,
            WalletService walletService
    ) {
        this.access = access;
        this.catalogService = catalogService;
        this.subscriptionService = subscriptionService;
        this.orderService = orderService;
        this.statisticsService = statisticsService;
        this.walletService = walletService;
    }

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
