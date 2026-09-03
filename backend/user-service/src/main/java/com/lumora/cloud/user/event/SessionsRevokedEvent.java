package com.lumora.cloud.user.event;

import java.time.Instant;
import java.util.List;

public record SessionsRevokedEvent(List<RevokedSession> sessions, Instant revokedAt) {

    public SessionsRevokedEvent {
        sessions = List.copyOf(sessions);
    }

    public record RevokedSession(String sessionId, Instant expiresAt) {
    }
}
