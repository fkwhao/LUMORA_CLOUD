package com.lumora.cloud.billing.messaging;

import java.time.Instant;

public record WalletTopupExpiryScheduledEvent(String orderNo, Instant expiresAt) {
}
