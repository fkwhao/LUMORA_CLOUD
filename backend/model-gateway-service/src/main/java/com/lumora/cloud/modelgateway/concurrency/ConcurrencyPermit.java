package com.lumora.cloud.modelgateway.concurrency;

import java.util.List;

public record ConcurrencyPermit(List<String> keys, String leaseId) {
    public ConcurrencyPermit {
        keys = List.copyOf(keys);
    }

    public ConcurrencyPermit(String userKey, String modelKey, String leaseId) {
        this(List.of(userKey, modelKey), leaseId);
    }
}
