package com.lumora.cloud.catalog.service;

import com.lumora.cloud.catalog.domain.dto.provider.CreateProviderRequest;
import com.lumora.cloud.catalog.domain.dto.provider.RotateProviderCredentialRequest;
import com.lumora.cloud.catalog.domain.dto.provider.UpdateProviderRequest;
import com.lumora.cloud.catalog.domain.vo.provider.ProviderResponse;

import java.util.List;

public interface IProviderService {

    ProviderResponse create(CreateProviderRequest request);

    ProviderResponse update(Long providerId, UpdateProviderRequest request);

    List<ProviderResponse> list();

    ProviderResponse rotateCredential(Long providerId, RotateProviderCredentialRequest request);
}
