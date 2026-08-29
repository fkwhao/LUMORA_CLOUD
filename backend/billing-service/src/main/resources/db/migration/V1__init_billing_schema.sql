CREATE TABLE billing_plan (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_billing_plan_code (code),
    KEY idx_billing_plan_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE billing_plan_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plan_id BIGINT UNSIGNED NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    monthly_price_minor BIGINT UNSIGNED NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    weekly_quota DECIMAL(20,6) UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_version_number (plan_id, version_no),
    KEY idx_plan_version_status (status, published_at),
    CONSTRAINT fk_plan_version_plan FOREIGN KEY (plan_id) REFERENCES billing_plan (id),
    CONSTRAINT chk_plan_version_currency CHECK (CHAR_LENGTH(currency) = 3),
    CONSTRAINT chk_plan_version_quota CHECK (weekly_quota > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE billing_account (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_billing_account_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE billing_subscription (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    plan_version_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    source VARCHAR(24) NOT NULL,
    source_reference VARCHAR(128) NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    canceled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_subscription_source_reference (source, source_reference),
    KEY idx_subscription_user_status_time (user_id, status, starts_at, ends_at),
    KEY idx_subscription_plan_version (plan_version_id),
    CONSTRAINT fk_subscription_account FOREIGN KEY (account_id) REFERENCES billing_account (id),
    CONSTRAINT fk_subscription_plan_version FOREIGN KEY (plan_version_id) REFERENCES billing_plan_version (id),
    CONSTRAINT chk_subscription_time CHECK (ends_at > starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quota_bucket (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    subscription_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    period_no INT UNSIGNED NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    granted_quota DECIMAL(20,6) UNSIGNED NOT NULL,
    reserved_quota DECIMAL(20,6) UNSIGNED NOT NULL DEFAULT 0,
    consumed_quota DECIMAL(20,6) UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quota_bucket_subscription_period (subscription_id, period_no),
    KEY idx_quota_bucket_user_time (user_id, starts_at, ends_at),
    CONSTRAINT fk_quota_bucket_subscription FOREIGN KEY (subscription_id) REFERENCES billing_subscription (id),
    CONSTRAINT chk_quota_bucket_time CHECK (ends_at > starts_at),
    CONSTRAINT chk_quota_bucket_totals CHECK (reserved_quota + consumed_quota <= granted_quota)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE billing_reservation (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    client_request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    model_code VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pricing_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quota_bucket_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    requested_quota DECIMAL(20,6) UNSIGNED NOT NULL,
    settled_quota DECIMAL(20,6) UNSIGNED NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
    failure_reason VARCHAR(255) NULL,
    expires_at DATETIME(6) NOT NULL,
    settled_at DATETIME(6) NULL,
    released_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_reservation_request (request_id),
    UNIQUE KEY uk_reservation_client_request (user_id, client_request_id),
    KEY idx_reservation_user_time (user_id, created_at),
    KEY idx_reservation_status_expires (status, expires_at),
    CONSTRAINT fk_reservation_bucket FOREIGN KEY (quota_bucket_id) REFERENCES quota_bucket (id),
    CONSTRAINT chk_reservation_quota CHECK (requested_quota > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE billing_usage_record (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    usage_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reservation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    model_code VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pricing_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_tokens BIGINT UNSIGNED NOT NULL,
    output_tokens BIGINT UNSIGNED NOT NULL,
    reasoning_tokens BIGINT UNSIGNED NOT NULL,
    cache_read_tokens BIGINT UNSIGNED NOT NULL,
    cache_write_tokens BIGINT UNSIGNED NOT NULL,
    billed_quota DECIMAL(20,6) UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_usage_record_usage (usage_id),
    UNIQUE KEY uk_usage_record_reservation (reservation_id),
    KEY idx_usage_record_user_time (user_id, occurred_at),
    CONSTRAINT fk_usage_record_reservation FOREIGN KEY (reservation_id) REFERENCES billing_reservation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quota_ledger (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    quota_bucket_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reservation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    entry_type VARCHAR(24) NOT NULL,
    reference_type VARCHAR(32) NOT NULL,
    reference_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    granted_delta DECIMAL(20,6) NOT NULL DEFAULT 0,
    reserved_delta DECIMAL(20,6) NOT NULL DEFAULT 0,
    consumed_delta DECIMAL(20,6) NOT NULL DEFAULT 0,
    description VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quota_ledger_reference (entry_type, reference_type, reference_id),
    KEY idx_quota_ledger_user_time (user_id, created_at),
    KEY idx_quota_ledger_bucket_time (quota_bucket_id, created_at),
    CONSTRAINT fk_quota_ledger_bucket FOREIGN KEY (quota_bucket_id) REFERENCES quota_bucket (id),
    CONSTRAINT fk_quota_ledger_reservation FOREIGN KEY (reservation_id) REFERENCES billing_reservation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
