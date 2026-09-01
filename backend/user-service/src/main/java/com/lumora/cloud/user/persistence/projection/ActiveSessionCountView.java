package com.lumora.cloud.user.persistence.projection;

public class ActiveSessionCountView {

    private Long userId;
    private Long activeSessions;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getActiveSessions() {
        return activeSessions;
    }

    public void setActiveSessions(Long activeSessions) {
        this.activeSessions = activeSessions;
    }
}
