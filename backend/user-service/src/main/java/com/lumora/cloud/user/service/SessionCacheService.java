package com.lumora.cloud.user.service;

import com.lumora.cloud.api.AuthHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class SessionCacheService {

    private final StringRedisTemplate redis;

    public SessionCacheService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void remember(String sessionId, Long userId, Instant expiresAt, Instant now) {
        Duration ttl = positiveDuration(now, expiresAt);
        redis.opsForValue().set(AuthHeaders.SESSION_CACHE_PREFIX + sessionId, userId.toString(), ttl);
        redis.delete(AuthHeaders.REVOKED_SESSION_PREFIX + sessionId);
    }

    public void revoke(String sessionId, Instant expiresAt, Instant now) {
        Duration ttl = positiveDuration(now, expiresAt);
        redis.delete(AuthHeaders.SESSION_CACHE_PREFIX + sessionId);
        redis.opsForValue().set(AuthHeaders.REVOKED_SESSION_PREFIX + sessionId, "1", ttl);
    }

    private Duration positiveDuration(Instant now, Instant expiresAt) {
        Duration ttl = Duration.between(now, expiresAt);
        return ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(15) : ttl;
    }
}
