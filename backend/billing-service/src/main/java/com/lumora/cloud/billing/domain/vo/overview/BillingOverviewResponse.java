package com.lumora.cloud.billing.domain.vo.overview;

import com.lumora.cloud.billing.domain.vo.plan.PlanResponse;
import com.lumora.cloud.billing.domain.vo.quota.QuotaResponse;
import com.lumora.cloud.billing.domain.vo.subscription.SubscriptionResponse;

public record BillingOverviewResponse(
        boolean hasActiveSubscription,
        PlanResponse plan,
        SubscriptionResponse subscription,
        QuotaResponse quota
) {
}
