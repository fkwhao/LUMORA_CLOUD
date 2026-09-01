package com.lumora.cloud.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SessionRevocationCacheListener {

    private static final Logger log = LoggerFactory.getLogger(SessionRevocationCacheListener.class);

    private final SessionCacheService sessionCache;

    public SessionRevocationCacheListener(SessionCacheService sessionCache) {
        this.sessionCache = sessionCache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void revoke(SessionsRevokedEvent event) {
        for (var session : event.sessions()) {
            try {
                sessionCache.revoke(session.sessionId(), session.expiresAt(), event.revokedAt());
            } catch (RuntimeException exception) {
                log.error("Could not publish revoked session {} to Redis", session.sessionId(), exception);
            }
        }
    }
}
