package com.lumora.cloud.user.config;

import com.lumora.cloud.user.domain.AuthTypes.ClientType;
import com.lumora.cloud.user.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    @Test
    void issuesSignedAccessTokenWithIdentityClaims() {
        AuthProperties properties = new AuthProperties(
                new AuthProperties.Jwt(
                        "unit-test-secret-with-at-least-thirty-two-characters",
                        "lumora-test",
                        "lumora-test-api",
                        Duration.ofMinutes(15)
                ),
                Duration.ofDays(30),
                new AuthProperties.Cookie("lumora_refresh", false, "Strict", "/api/app/auth")
        );
        JwtConfiguration configuration = new JwtConfiguration();
        SecretKey key = configuration.lumoraJwtSecretKey(properties);
        JwtEncoder encoder = configuration.jwtEncoder(key);
        JwtDecoder decoder = configuration.jwtDecoder(key, properties);
        JwtTokenService service = new JwtTokenService(encoder, properties);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        JwtTokenService.AccessToken token = service.issue(
                42L,
                "session-1",
                "device-1",
                ClientType.WEB,
                Set.of("USER", "ADMIN"),
                3,
                now
        );
        Jwt jwt = decoder.decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("sid")).isEqualTo("session-1");
        assertThat(jwt.getClaimAsString("device_id")).isEqualTo("device-1");
        assertThat(jwt.getClaimAsString("client_type")).isEqualTo("WEB");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN", "USER");
        assertThat(jwt.getClaimAsString("token_type")).isEqualTo("access");
        assertThat(jwt.<Number>getClaim("token_version").intValue()).isEqualTo(3);
        assertThat(token.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
    }
}
