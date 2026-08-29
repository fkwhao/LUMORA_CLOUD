package com.lumora.cloud.catalog.service;

public record EncryptedCredential(
        byte[] ciphertext,
        byte[] nonce,
        int keyVersion,
        String fingerprint,
        String hint
) {
}
