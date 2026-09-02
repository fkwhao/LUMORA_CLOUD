ALTER TABLE model_provider
    ADD COLUMN max_concurrency INT UNSIGNED NULL AFTER credential_reference,
    ADD COLUMN requests_per_minute INT UNSIGNED NULL AFTER max_concurrency,
    ADD COLUMN tokens_per_minute BIGINT UNSIGNED NULL AFTER requests_per_minute,
    ADD CONSTRAINT chk_model_provider_max_concurrency
        CHECK (max_concurrency IS NULL OR max_concurrency > 0),
    ADD CONSTRAINT chk_model_provider_rpm
        CHECK (requests_per_minute IS NULL OR requests_per_minute > 0),
    ADD CONSTRAINT chk_model_provider_tpm
        CHECK (tokens_per_minute IS NULL OR tokens_per_minute > 0);

CREATE TABLE model_upstream_route (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_id BIGINT UNSIGNED NOT NULL,
    route_name VARCHAR(120) NOT NULL,
    upstream_model VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    protocol_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    base_url VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    credential_reference VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    route_priority INT UNSIGNED NOT NULL DEFAULT 100,
    route_weight INT UNSIGNED NOT NULL DEFAULT 100,
    max_concurrency INT UNSIGNED NULL,
    requests_per_minute INT UNSIGNED NULL,
    tokens_per_minute BIGINT UNSIGNED NULL,
    failover_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    circuit_breaker_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    cost_currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_cost_per_million DECIMAL(20, 8) NOT NULL,
    output_cost_per_million DECIMAL(20, 8) NOT NULL,
    cache_read_cost_per_million DECIMAL(20, 8) NOT NULL,
    cache_write_cost_per_million DECIMAL(20, 8) NOT NULL DEFAULT 0,
    cost_time_pricing_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    cost_time_pricing_zone VARCHAR(64) NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_model_route_name (version_id, route_name),
    KEY idx_model_route_version_status (version_id, status, route_priority),
    KEY idx_model_route_provider (provider_id),
    CONSTRAINT fk_model_route_version FOREIGN KEY (version_id) REFERENCES model_config_version (id) ON DELETE CASCADE,
    CONSTRAINT fk_model_route_provider FOREIGN KEY (provider_id) REFERENCES model_provider (id),
    CONSTRAINT chk_model_route_priority CHECK (route_priority <= 10000),
    CONSTRAINT chk_model_route_weight CHECK (route_weight BETWEEN 1 AND 10000),
    CONSTRAINT chk_model_route_max_concurrency CHECK (max_concurrency IS NULL OR max_concurrency > 0),
    CONSTRAINT chk_model_route_rpm CHECK (requests_per_minute IS NULL OR requests_per_minute > 0),
    CONSTRAINT chk_model_route_tpm CHECK (tokens_per_minute IS NULL OR tokens_per_minute > 0),
    CONSTRAINT chk_model_route_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_model_route_costs CHECK (
        input_cost_per_million >= 0 AND output_cost_per_million >= 0
        AND cache_read_cost_per_million >= 0 AND cache_write_cost_per_million >= 0
    ),
    CONSTRAINT chk_model_route_cost_time CHECK (
        (cost_time_pricing_enabled = FALSE AND cost_time_pricing_zone IS NULL)
        OR (cost_time_pricing_enabled = TRUE AND cost_time_pricing_zone IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE model_route_cost_pricing_rule (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    route_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    rule_order INT UNSIGNED NOT NULL,
    name VARCHAR(80) NOT NULL,
    days_mask TINYINT UNSIGNED NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    input_cost_per_million DECIMAL(20, 8) NOT NULL,
    output_cost_per_million DECIMAL(20, 8) NOT NULL,
    cache_read_cost_per_million DECIMAL(20, 8) NOT NULL,
    cache_write_cost_per_million DECIMAL(20, 8) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_route_cost_rule_order (route_id, rule_order),
    CONSTRAINT fk_route_cost_rule_route FOREIGN KEY (route_id) REFERENCES model_upstream_route (id) ON DELETE CASCADE,
    CONSTRAINT chk_route_cost_days CHECK (days_mask BETWEEN 1 AND 127),
    CONSTRAINT chk_route_cost_window CHECK (start_time <> end_time),
    CONSTRAINT chk_route_cost_values CHECK (
        input_cost_per_million >= 0 AND output_cost_per_million >= 0
        AND cache_read_cost_per_million >= 0 AND cache_write_cost_per_million >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO model_upstream_route (
    id, version_id, provider_id, route_name, upstream_model, protocol_type, base_url,
    credential_reference, route_priority, route_weight, failover_enabled,
    circuit_breaker_enabled, status, is_primary, cost_currency,
    input_cost_per_million, output_cost_per_million, cache_read_cost_per_million,
    cache_write_cost_per_million, cost_time_pricing_enabled, cost_time_pricing_zone
)
SELECT UUID(), v.id, v.provider_id, CONCAT('默认路由 · ', p.name), v.upstream_model,
       v.protocol_type, v.base_url, v.credential_reference, 100, 100, TRUE, TRUE,
       'ACTIVE', TRUE, v.cost_currency, v.input_cost_per_million,
       v.output_cost_per_million, v.cache_read_cost_per_million,
       v.cache_write_cost_per_million, v.cost_time_pricing_enabled,
       v.cost_time_pricing_zone
FROM model_config_version v
JOIN model_provider p ON p.id = v.provider_id;

INSERT INTO model_route_cost_pricing_rule (
    id, route_id, rule_order, name, days_mask, start_time, end_time,
    input_cost_per_million, output_cost_per_million,
    cache_read_cost_per_million, cache_write_cost_per_million
)
SELECT UUID(), r.id, t.rule_order, t.name, t.days_mask, t.start_time, t.end_time,
       t.input_cost_per_million, t.output_cost_per_million,
       t.cache_read_cost_per_million, t.cache_write_cost_per_million
FROM model_upstream_route r
JOIN model_time_pricing_rule t ON t.version_id = r.version_id AND t.pricing_scope = 'COST'
WHERE r.is_primary = TRUE;
