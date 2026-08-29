package com.lumora.cloud.user.domain;

public final class AuthTypes {

    private AuthTypes() {
    }

    public enum ClientType {
        WEB,
        DESKTOP
    }

    public enum UserStatus {
        ACTIVE,
        DISABLED
    }

    public enum SessionStatus {
        ACTIVE,
        REVOKED,
        EXPIRED
    }

    public enum RefreshTokenStatus {
        ACTIVE,
        USED,
        REVOKED
    }

    public enum LoginOutcome {
        SUCCESS,
        FAILURE
    }
}
