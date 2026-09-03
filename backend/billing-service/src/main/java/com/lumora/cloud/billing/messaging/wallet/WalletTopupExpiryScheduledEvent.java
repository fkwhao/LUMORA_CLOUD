package com.lumora.cloud.billing.messaging.wallet;

import java.time.Instant;

public record WalletTopupExpiryScheduledEvent(String orderNo, Instant expiresAt) {
}
