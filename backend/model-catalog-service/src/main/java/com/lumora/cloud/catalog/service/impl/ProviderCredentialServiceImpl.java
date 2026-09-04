package com.lumora.cloud.catalog.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lumora.cloud.api.UserContextHolder;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedProviderCredential;
import com.lumora.cloud.catalog.error.ApiException;
import com.lumora.cloud.catalog.domain.entity.provider.ProviderCredentialAuditEntity;
import com.lumora.cloud.catalog.domain.entity.provider.ProviderCredentialEntity;
import com.lumora.cloud.catalog.domain.model.CredentialRotation;
import com.lumora.cloud.catalog.domain.model.EncryptedCredential;
import com.lumora.cloud.catalog.mapper.provider.ProviderCredentialAuditMapper;
import com.lumora.cloud.catalog.mapper.provider.ProviderCredentialMapper;
import com.lumora.cloud.catalog.domain.vo.provider.ProviderCredentialStatusResponse;
import com.lumora.cloud.catalog.service.IProviderCredentialService;
import com.lumora.cloud.catalog.utils.ProviderCredentialCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProviderCredentialServiceImpl implements IProviderCredentialService {

    public static final String MANAGED_REFERENCE_PREFIX = "cred_";

    private final ProviderCredentialMapper credentialMapper;
    private final ProviderCredentialAuditMapper auditMapper;
    private final ProviderCredentialCipher cipher;

    @Override
    public String newReference() {
        return MANAGED_REFERENCE_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public void create(Long providerId, String reference, String apiKey) {
        EncryptedCredential encrypted = cipher.encrypt(reference, normalize(apiKey));
        ProviderCredentialEntity entity = ProviderCredentialEntity.create(
                UUID.randomUUID().toString(), providerId, reference,
                encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion(),
                encrypted.fingerprint(), encrypted.hint()
        );
        credentialMapper.insert(entity);
        auditMapper.insert(ProviderCredentialAuditEntity.create(
                entity.getId(), providerId, "CREATED", actorUserId(), encrypted.fingerprint()
        ));
    }

    @Override
    public CredentialRotation rotate(Long providerId, String apiKey) {
        ProviderCredentialEntity existing = credentialMapper.findByProviderIdForUpdate(providerId);
        String reference = existing == null ? newReference() : existing.getCredentialReference();
        EncryptedCredential encrypted = cipher.encrypt(reference, normalize(apiKey));
        if (existing == null) {
            ProviderCredentialEntity created = ProviderCredentialEntity.create(
                    UUID.randomUUID().toString(), providerId, reference,
                    encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion(),
                    encrypted.fingerprint(), encrypted.hint()
            );
            credentialMapper.insert(created);
            auditMapper.insert(ProviderCredentialAuditEntity.create(
                    created.getId(), providerId, "MIGRATED_FROM_ENVIRONMENT", actorUserId(), encrypted.fingerprint()
            ));
            return new CredentialRotation(reference, created.getId());
        }
        if (credentialMapper.rotate(
                existing.getId(), existing.getRevision(), reference,
                encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion(),
                encrypted.fingerprint(), encrypted.hint()
        ) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "CREDENTIAL_REVISION_CONFLICT",
                    "供应商凭据已被其他操作更新，请刷新后重试");
        }
        auditMapper.insert(ProviderCredentialAuditEntity.create(
                existing.getId(), providerId, "ROTATED", actorUserId(), encrypted.fingerprint()
        ));
        return new CredentialRotation(reference, existing.getId());
    }

    @Transactional(readOnly = true)
    @Override
    public ResolvedProviderCredential resolve(String reference) {
        if (reference == null || !reference.startsWith(MANAGED_REFERENCE_PREFIX) || reference.length() > 64) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROVIDER_CREDENTIAL_NOT_FOUND", "供应商凭据不存在");
        }
        ProviderCredentialEntity credential = credentialMapper.findByReference(reference);
        if (credential == null || !"ACTIVE".equals(credential.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROVIDER_CREDENTIAL_NOT_FOUND", "供应商凭据不存在");
        }
        return new ResolvedProviderCredential(
                credential.getCredentialReference(), cipher.decrypt(credential),
                credential.getSecretFingerprint(), credential.getRotatedAt()
        );
    }

    @Transactional(readOnly = true)
    @Override
    public Map<Long, ProviderCredentialStatusResponse> statuses(Collection<Long> providerIds) {
        if (providerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return credentialMapper.selectList(Wrappers.<ProviderCredentialEntity>lambdaQuery()
                        .in(ProviderCredentialEntity::getProviderId, providerIds))
                .stream()
                .collect(Collectors.toMap(
                        ProviderCredentialEntity::getProviderId,
                        this::managedStatus,
                        (left, right) -> left
                ));
    }

    @Override
    public ProviderCredentialStatusResponse status(Long providerId, String reference) {
        ProviderCredentialEntity credential = credentialMapper.selectOne(
                Wrappers.<ProviderCredentialEntity>lambdaQuery()
                        .eq(ProviderCredentialEntity::getProviderId, providerId)
                        .last("LIMIT 1")
        );
        return credential == null ? environmentStatus(reference) : managedStatus(credential);
    }

    @Override
    public ProviderCredentialStatusResponse environmentStatus(String reference) {
        return new ProviderCredentialStatusResponse(
                "ENVIRONMENT_REFERENCE", false, "由环境变量托管", null, 0, null
        );
    }

    private ProviderCredentialStatusResponse managedStatus(ProviderCredentialEntity credential) {
        return new ProviderCredentialStatusResponse(
                "ENCRYPTED_DATABASE", true, credential.getSecretHint(), credential.getSecretFingerprint(),
                credential.getRevision(), credential.getRotatedAt()
        );
    }

    private String normalize(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROVIDER_API_KEY_REQUIRED", "供应商 API Key 不能为空");
        }
        String normalized = apiKey.trim();
        if (normalized.length() > 8192) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROVIDER_API_KEY_TOO_LONG",
                    "供应商 API Key 长度不能超过 8192");
        }
        return normalized;
    }

    private String actorUserId() {
        return UserContextHolder.current().map(context -> context.userId()).orElse("system");
    }

}
