package com.lumora.cloud.billing.domain.vo.order;

import java.util.List;

public record PaymentCapabilitiesResponse(List<String> availableMethods) {
    public PaymentCapabilitiesResponse {
        availableMethods = List.copyOf(availableMethods);
    }
}
