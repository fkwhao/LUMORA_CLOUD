package com.lumora.cloud.api.fallback;

public class RemoteServiceUnavailableException extends RuntimeException {

    private final String serviceName;
    private final String operation;

    public RemoteServiceUnavailableException(
            String serviceName,
            String operation,
            Throwable cause
    ) {
        super("Remote service protection activated: " + serviceName + "#" + operation, cause);
        this.serviceName = serviceName;
        this.operation = operation;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getOperation() {
        return operation;
    }
}
