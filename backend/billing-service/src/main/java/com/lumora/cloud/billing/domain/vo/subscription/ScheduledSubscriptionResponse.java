package com.lumora.cloud.billing.domain.vo.subscription;

import com.lumora.cloud.billing.domain.vo.plan.PlanResponse;

public record ScheduledSubscriptionResponse(SubscriptionResponse subscription, PlanResponse plan) {
}
