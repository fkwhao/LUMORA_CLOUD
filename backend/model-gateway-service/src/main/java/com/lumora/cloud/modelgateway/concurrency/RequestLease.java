package com.lumora.cloud.modelgateway.concurrency;

public record RequestLease(String key, String token, String billingRequestId) {
}
