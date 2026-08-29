package com.lumora.cloud.modelgateway.service;

import com.lumora.cloud.api.billing.BillingClient;
import com.lumora.cloud.api.billing.BillingContracts.ReservationResponse;
import com.lumora.cloud.api.billing.BillingContracts.ReserveRequest;
import com.lumora.cloud.modelgateway.error.ApiException;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class BillingControlService {

    private final BillingClient billingClient;

    public BillingControlService(BillingClient billingClient) {
        this.billingClient = billingClient;
    }

    public Mono<ReservationResponse> reserve(ReserveRequest request) {
        return Mono.fromCallable(() -> billingClient.reserve(request))
                .subscribeOn(Schedulers.boundedElastic())
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE, "BILLING_INVALID_RESPONSE",
                        "计费服务返回了空响应"
                )))
                .onErrorMap(FeignException.class, this::billingError);
    }

    private ApiException billingError(FeignException exception) {
        if (exception.status() == HttpStatus.PAYMENT_REQUIRED.value()) {
            return new ApiException(HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_QUOTA", "当前套餐额度不足", exception);
        }
        if (exception.status() == HttpStatus.NOT_FOUND.value()) {
            return new ApiException(HttpStatus.PAYMENT_REQUIRED, "ACTIVE_SUBSCRIPTION_REQUIRED",
                    "当前账号没有可用套餐", exception);
        }
        if (exception.status() == HttpStatus.CONFLICT.value()) {
            return new ApiException(HttpStatus.CONFLICT, "BILLING_REQUEST_CONFLICT",
                    "模型请求的计费幂等状态发生冲突", exception);
        }
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BILLING_UNAVAILABLE",
                "计费服务暂时不可用", exception);
    }
}
