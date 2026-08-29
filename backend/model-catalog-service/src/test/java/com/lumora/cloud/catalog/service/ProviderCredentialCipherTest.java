package com.lumora.cloud.catalog.service;

import com.lumora.cloud.catalog.config.CredentialCryptoProperties;
import com.lumora.cloud.catalog.error.ApiException;
import com.lumora.cloud.catalog.persistence.entity.ProviderCredentialEntity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderCredentialCipherTest {

    private static final String TEST_MASTER_KEY =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final ProviderCredentialCipher cipher = new ProviderCredentialCipher(
            new CredentialCryptoProperties(TEST_MASTER_KEY, 1)
    );

    @Test
    void encryptsWithUniqueNonceAndDecryptsOnlyWithMatchingReference() {
        String secret = "sk-provider-super-secret-value";
        EncryptedCredential first = cipher.encrypt("cred_first", secret);
        EncryptedCredential second = cipher.encrypt("cred_first", secret);

        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(new String(first.ciphertext(), StandardCharsets.UTF_8)).doesNotContain(secret);
        assertThat(first.hint()).isEqualTo("sk-p••••alue");
        assertThat(first.fingerprint()).hasSize(16);

        ProviderCredentialEntity stored = entity("cred_first", first);
        assertThat(cipher.decrypt(stored)).isEqualTo(secret);

        ProviderCredentialEntity wrongReference = ProviderCredentialEntity.create(
                "id", 1L, "cred_other", first.ciphertext(), first.nonce(), first.keyVersion(),
                first.fingerprint(), first.hint()
        );
        assertThatThrownBy(() -> cipher.decrypt(wrongReference))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("PROVIDER_CREDENTIAL_UNAVAILABLE");
    }

    @Test
    void rejectsTamperedCiphertext() {
        EncryptedCredential encrypted = cipher.encrypt("cred_tampered", "secret");
        byte[] tampered = Arrays.copyOf(encrypted.ciphertext(), encrypted.ciphertext().length);
        tampered[0] ^= 1;
        ProviderCredentialEntity stored = ProviderCredentialEntity.create(
                "id", 1L, "cred_tampered", tampered, encrypted.nonce(), encrypted.keyVersion(),
                encrypted.fingerprint(), encrypted.hint()
        );

        assertThatThrownBy(() -> cipher.decrypt(stored))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("PROVIDER_CREDENTIAL_UNAVAILABLE");
    }

    private ProviderCredentialEntity entity(String reference, EncryptedCredential encrypted) {
        return ProviderCredentialEntity.create(
                "id", 1L, reference, encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion(),
                encrypted.fingerprint(), encrypted.hint()
        );
    }
}
