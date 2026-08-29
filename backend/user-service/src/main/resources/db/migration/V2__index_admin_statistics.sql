CREATE INDEX idx_user_account_created
    ON user_account (created_at);

CREATE INDEX idx_user_session_status_expiry
    ON user_session (status, expires_at);
