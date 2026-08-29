CREATE TABLE model_provider (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(120) NOT NULL,
    protocol_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    base_url VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    credential_reference VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_model_provider_code (code),
    KEY idx_model_provider_status (status),
    CONSTRAINT chk_model_provider_revision CHECK (revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE model_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_model_definition_code (code),
    KEY idx_model_definition_status (status),
    CONSTRAINT chk_model_definition_revision CHECK (revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE model_config_version (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    model_id BIGINT UNSIGNED NOT NULL,
    provider_id BIGINT UNSIGNED NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    upstream_model VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    protocol_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    base_url VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    credential_reference VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    context_window BIGINT UNSIGNED NOT NULL,
    max_output_tokens BIGINT UNSIGNED NOT NULL,
    supports_reasoning BOOLEAN NOT NULL DEFAULT FALSE,
    supports_tools BOOLEAN NOT NULL DEFAULT FALSE,
    supports_vision BOOLEAN NOT NULL DEFAULT FALSE,
    supports_json BOOLEAN NOT NULL DEFAULT FALSE,
    cost_currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_cost_per_million DECIMAL(20,6) NOT NULL DEFAULT 0,
    output_cost_per_million DECIMAL(20,6) NOT NULL DEFAULT 0,
    reasoning_cost_per_million DECIMAL(20,6) NOT NULL DEFAULT 0,
    cache_read_cost_per_million DECIMAL(20,6) NOT NULL DEFAULT 0,
    cache_write_cost_per_million DECIMAL(20,6) NOT NULL DEFAULT 0,
    input_quota_per_million DECIMAL(20,6) NOT NULL DEFAULT 0,
    output_quota_per_million DECIMAL(20,6) NOT NULL DEFAULT 0,
    reasoning_quota_per_million DECIMAL(20,6) NOT NULL DEFAULT 0,
    cache_read_quota_per_million DECIMAL(20,6) NOT NULL DEFAULT 0,
    cache_write_quota_per_million DECIMAL(20,6) NOT NULL DEFAULT 0,
    minimum_request_quota DECIMAL(20,6) NOT NULL DEFAULT 0,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    draft_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'DRAFT' THEN 1 ELSE NULL END) STORED,
    published_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'PUBLISHED' THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_model_version_key (version_key),
    UNIQUE KEY uk_model_version_number (model_id, version_no),
    UNIQUE KEY uk_model_single_draft (model_id, draft_guard),
    UNIQUE KEY uk_model_single_published (model_id, published_guard),
    KEY idx_model_version_provider (provider_id),
    KEY idx_model_version_status (status, published_at),
    CONSTRAINT fk_model_version_model FOREIGN KEY (model_id) REFERENCES model_definition (id),
    CONSTRAINT fk_model_version_provider FOREIGN KEY (provider_id) REFERENCES model_provider (id),
    CONSTRAINT chk_model_version_limits CHECK (context_window > 0 AND max_output_tokens > 0 AND max_output_tokens <= context_window),
    CONSTRAINT chk_model_version_currency CHECK (CHAR_LENGTH(cost_currency) = 3),
    CONSTRAINT chk_model_version_costs CHECK (
        input_cost_per_million >= 0 AND output_cost_per_million >= 0
        AND reasoning_cost_per_million >= 0 AND cache_read_cost_per_million >= 0
        AND cache_write_cost_per_million >= 0
    ),
    CONSTRAINT chk_model_version_quotas CHECK (
        input_quota_per_million >= 0 AND output_quota_per_million >= 0
        AND reasoning_quota_per_million >= 0 AND cache_read_quota_per_million >= 0
        AND cache_write_quota_per_million >= 0 AND minimum_request_quota >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
