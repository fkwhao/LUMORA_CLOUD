CREATE TABLE wallet_account (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    available_minor BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_account_user_currency (user_id, currency),
    KEY idx_wallet_account_user (user_id),
    CONSTRAINT chk_wallet_account_currency CHECK (CHAR_LENGTH(currency) = 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wallet_topup_order (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    order_no VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    amount_minor BIGINT UNSIGNED NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT',
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    paid_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_topup_order_no (order_no),
    UNIQUE KEY uk_wallet_topup_user_idempotency (user_id, idempotency_key),
    KEY idx_wallet_topup_user_created (user_id, created_at, id),
    KEY idx_wallet_topup_status_expiry (status, expires_at),
    CONSTRAINT fk_wallet_topup_account FOREIGN KEY (account_id) REFERENCES wallet_account (id),
    CONSTRAINT chk_wallet_topup_amount CHECK (amount_minor > 0),
    CONSTRAINT chk_wallet_topup_currency CHECK (CHAR_LENGTH(currency) = 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wallet_ledger (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    entry_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reference_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reference_id VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount_delta BIGINT NOT NULL,
    balance_after BIGINT UNSIGNED NOT NULL,
    description VARCHAR(500) NULL,
    actor_user_id BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_ledger_reference (entry_type, reference_type, reference_id),
    KEY idx_wallet_ledger_user_created (user_id, created_at, id),
    KEY idx_wallet_ledger_account_created (account_id, created_at, id),
    CONSTRAINT fk_wallet_ledger_account FOREIGN KEY (account_id) REFERENCES wallet_account (id),
    CONSTRAINT chk_wallet_ledger_currency CHECK (CHAR_LENGTH(currency) = 3),
    CONSTRAINT chk_wallet_ledger_nonzero CHECK (amount_delta <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE billing_purchase_order
    ADD COLUMN payment_provider VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER status;
