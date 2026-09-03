package com.lumora.cloud.catalog.domain.model;

public record EncryptedCredential(
        byte[] ciphertext,
        byte[] nonce,
        int keyVersion,
        String fingerprint,
        String hint
) {
}
