package com.lumora.cloud.catalog.service;

import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedProviderCredential;
import com.lumora.cloud.catalog.domain.model.CredentialRotation;
import com.lumora.cloud.catalog.domain.vo.provider.ProviderCredentialStatusResponse;

import java.util.Collection;
import java.util.Map;

public interface IProviderCredentialService {

    String newReference();

    void create(Long providerId, String reference, String apiKey);

    CredentialRotation rotate(Long providerId, String apiKey);

    ResolvedProviderCredential resolve(String reference);

    Map<Long, ProviderCredentialStatusResponse> statuses(Collection<Long> providerIds);

    ProviderCredentialStatusResponse status(Long providerId, String reference);

    ProviderCredentialStatusResponse environmentStatus(String reference);
}
