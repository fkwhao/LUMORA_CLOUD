package com.lumora.cloud.api.fallback;

import feign.FeignException;

final class RemoteServiceFallbacks {

    private RemoteServiceFallbacks() {
    }

    static RuntimeException failure(String serviceName, String operation, Throwable cause) {
        FeignException feignFailure = findFeignFailure(cause);
        if (feignFailure != null) {
            return feignFailure;
        }
        return new RemoteServiceUnavailableException(serviceName, operation, cause);
    }

    private static FeignException findFeignFailure(Throwable cause) {
        Throwable current = cause;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof FeignException feignException) {
                return feignException;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }
}
