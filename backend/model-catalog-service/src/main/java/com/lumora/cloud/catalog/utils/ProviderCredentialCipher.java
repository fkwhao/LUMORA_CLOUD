package com.lumora.cloud.catalog.utils;

import com.lumora.cloud.catalog.domain.model.EncryptedCredential;
import com.lumora.cloud.catalog.config.CredentialCryptoProperties;
import com.lumora.cloud.catalog.error.ApiException;
import com.lumora.cloud.catalog.domain.entity.provider.ProviderCredentialEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class ProviderCredentialCipher {

    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec masterKey;
    private final int keyVersion;

    public ProviderCredentialCipher(CredentialCryptoProperties properties) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.masterKey().trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("LUMORA_CREDENTIAL_MASTER_KEY must be valid Base64", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("LUMORA_CREDENTIAL_MASTER_KEY must decode to exactly 32 bytes");
        }
        this.masterKey = new SecretKeySpec(decoded, "AES");
        this.keyVersion = properties.keyVersion();
    }

    public EncryptedCredential encrypt(String reference, String plaintext) {
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        byte[] secret = plaintext.getBytes(StandardCharsets.UTF_8);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(reference.getBytes(StandardCharsets.UTF_8));
            return new EncryptedCredential(
                    cipher.doFinal(secret), nonce, keyVersion, fingerprint(secret), hint(plaintext)
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Provider credential encryption failed", exception);
        }
    }

    public String decrypt(ProviderCredentialEntity credential) {
        if (credential.getEncryptionKeyVersion() != keyVersion) {
            throw unavailable("供应商凭据使用了当前服务不支持的主密钥版本");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, masterKey,
                    new GCMParameterSpec(GCM_TAG_BITS, credential.getEncryptionNonce()));
            cipher.updateAAD(credential.getCredentialReference().getBytes(StandardCharsets.UTF_8));
            byte[] plaintext = cipher.doFinal(credential.getEncryptedSecret());
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw unavailable("供应商凭据无法解密，请检查平台主密钥");
        }
    }

    private String fingerprint(byte[] secret) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(secret);
            return HexFormat.of().withUpperCase().formatHex(hash).substring(0, 16);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String hint(String plaintext) {
        int length = plaintext.length();
        if (length <= 4) {
            return "••••";
        }
        if (length <= 8) {
            return plaintext.substring(0, 2) + "••••" + plaintext.substring(length - 2);
        }
        return plaintext.substring(0, 4) + "••••" + plaintext.substring(length - 4);
    }

    private ApiException unavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_CREDENTIAL_UNAVAILABLE", message);
    }
}
