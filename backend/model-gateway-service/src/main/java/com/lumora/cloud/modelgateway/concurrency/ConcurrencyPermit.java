package com.lumora.cloud.modelgateway.concurrency;

public record ConcurrencyPermit(String userKey, String modelKey, String leaseId) {
}
