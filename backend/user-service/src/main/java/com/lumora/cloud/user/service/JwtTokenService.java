package com.lumora.cloud.user.service;

import com.lumora.cloud.user.config.AuthProperties;
import com.lumora.cloud.user.domain.AuthTypes.ClientType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final AuthProperties properties;

    public JwtTokenService(JwtEncoder encoder, AuthProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public AccessToken issue(
            Long userId,
            String sessionId,
            String deviceId,
            ClientType clientType,
            Set<String> roles,
            int tokenVersion,
            Instant now
    ) {
        Instant expiresAt = now.plus(properties.jwt().accessTokenTtl());
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .audience(java.util.List.of(properties.jwt().audience()))
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("token_type", "access")
                .claim("sid", sessionId)
                .claim("device_id", deviceId)
                .claim("client_type", clientType.name())
                .claim("roles", roles.stream().sorted().toList())
                .claim("token_version", tokenVersion)
                .build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, expiresAt);
    }

    public record AccessToken(String value, Instant expiresAt) {
    }
}
