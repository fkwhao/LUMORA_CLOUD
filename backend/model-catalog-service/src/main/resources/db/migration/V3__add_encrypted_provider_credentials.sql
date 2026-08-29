CREATE TABLE model_provider_credential (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_id BIGINT UNSIGNED NOT NULL,
    credential_reference VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    encrypted_secret VARBINARY(16384) NOT NULL,
    encryption_nonce BINARY(12) NOT NULL,
    encryption_key_version INT UNSIGNED NOT NULL,
    secret_fingerprint CHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    secret_hint VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    rotated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_credential_provider (provider_id),
    UNIQUE KEY uk_provider_credential_reference (credential_reference),
    KEY idx_provider_credential_status (status),
    CONSTRAINT fk_provider_credential_provider FOREIGN KEY (provider_id) REFERENCES model_provider (id),
    CONSTRAINT chk_provider_credential_revision CHECK (revision >= 0),
    CONSTRAINT chk_provider_credential_key_version CHECK (encryption_key_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE model_provider_credential_audit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    credential_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_user_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    secret_fingerprint CHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_credential_audit_provider_time (provider_id, occurred_at),
    CONSTRAINT fk_credential_audit_credential FOREIGN KEY (credential_id) REFERENCES model_provider_credential (id),
    CONSTRAINT fk_credential_audit_provider FOREIGN KEY (provider_id) REFERENCES model_provider (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
