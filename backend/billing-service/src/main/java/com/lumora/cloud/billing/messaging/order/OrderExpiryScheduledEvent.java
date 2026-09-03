package com.lumora.cloud.billing.messaging.order;

import java.time.Instant;

public record OrderExpiryScheduledEvent(String orderNo, Instant expiresAt) {
}
