package com.lumora.cloud.modelgateway.concurrency;

import com.lumora.cloud.modelgateway.error.ApiException;
import org.springframework.http.HttpStatus;

public class RouteCapacityException extends ApiException {

    public RouteCapacityException(String code, String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, code, message);
    }
}
