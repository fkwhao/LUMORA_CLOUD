package com.lumora.cloud.modelgateway.provider;

public interface ProviderCredentialResolver {

    String resolve(String reference);

    default void invalidate(String reference) {
    }
}
