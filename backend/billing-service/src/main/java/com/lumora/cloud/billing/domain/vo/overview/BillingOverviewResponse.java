package com.lumora.cloud.billing.domain.vo.overview;

import com.lumora.cloud.billing.domain.vo.plan.PlanResponse;
import com.lumora.cloud.billing.domain.vo.quota.QuotaResponse;
import com.lumora.cloud.billing.domain.vo.subscription.SubscriptionResponse;
import com.lumora.cloud.billing.domain.vo.subscription.ScheduledSubscriptionResponse;
import java.util.List;

public record BillingOverviewResponse(
        boolean hasActiveSubscription,
        PlanResponse plan,
        SubscriptionResponse subscription,
        QuotaResponse quota,
        List<ScheduledSubscriptionResponse> scheduledSubscriptions
) {
    public BillingOverviewResponse(boolean active, PlanResponse plan, SubscriptionResponse subscription, QuotaResponse quota) {
        this(active, plan, subscription, quota, List.of());
    }
}
