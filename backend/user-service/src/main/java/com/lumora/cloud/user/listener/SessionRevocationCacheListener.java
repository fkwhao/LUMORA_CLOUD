package com.lumora.cloud.user.listener;

import com.lumora.cloud.user.cache.SessionCacheService;
import com.lumora.cloud.user.event.SessionsRevokedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SessionRevocationCacheListener {

    private static final Logger log = LoggerFactory.getLogger(SessionRevocationCacheListener.class);

    private final SessionCacheService sessionCache;

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
