package com.lumora.cloud.user.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCodecTest {

    private final RefreshTokenCodec codec = new RefreshTokenCodec();

    @Test
    void generatesOpaqueUrlSafeTokensAndStoresOnlyStableHashes() {
        String first = codec.generate();
        String second = codec.generate();

        assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(second).isNotEqualTo(first);
        assertThat(codec.hash(first)).hasSize(64).matches("[0-9a-f]+");
        assertThat(codec.hash(first)).isEqualTo(codec.hash(first));
        assertThat(codec.hash(second)).isNotEqualTo(codec.hash(first));
    }
}
