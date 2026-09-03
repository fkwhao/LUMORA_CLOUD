package com.lumora.cloud.billing.domain.vo.subscription;

import java.time.Instant;

public record SubscriptionResponse(
        String subscriptionId,
        Long userId,
        Long planVersionId,
        String status,
        String source,
        String sourceReference,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt
) {
}
