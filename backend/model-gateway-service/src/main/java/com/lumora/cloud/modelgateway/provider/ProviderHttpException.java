package com.lumora.cloud.modelgateway.provider;

import org.springframework.http.HttpStatusCode;

public class ProviderHttpException extends RuntimeException {

    private final HttpStatusCode status;

    public ProviderHttpException(HttpStatusCode status) {
        super("Model provider returned HTTP " + status.value());
        this.status = status;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public boolean isDefinitiveRejection() {
        return status.is4xxClientError() && status.value() != 408 && status.value() != 429;
    }
}
