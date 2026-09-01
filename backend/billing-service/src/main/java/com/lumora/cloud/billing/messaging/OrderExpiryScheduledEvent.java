package com.lumora.cloud.billing.messaging;

import java.time.Instant;

public record OrderExpiryScheduledEvent(String orderNo, Instant expiresAt) {
}
