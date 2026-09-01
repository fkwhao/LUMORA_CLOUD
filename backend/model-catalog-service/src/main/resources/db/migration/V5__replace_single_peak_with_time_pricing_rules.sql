ALTER TABLE model_config_version
    ADD COLUMN default_quota_multiplier DECIMAL(12,6) NOT NULL DEFAULT 1 AFTER minimum_request_quota;

CREATE TABLE model_time_pricing_rule (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    rule_order INT UNSIGNED NOT NULL,
    name VARCHAR(80) NOT NULL,
    days_mask SMALLINT UNSIGNED NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    cost_override_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    input_cost_per_million DECIMAL(20,6) NULL,
    output_cost_per_million DECIMAL(20,6) NULL,
    cache_read_cost_per_million DECIMAL(20,6) NULL,
    cache_write_cost_per_million DECIMAL(20,6) NULL,
    quota_multiplier DECIMAL(12,6) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_time_pricing_rule_order (version_id, rule_order),
    KEY idx_time_pricing_rule_version (version_id),
    CONSTRAINT fk_time_pricing_rule_version FOREIGN KEY (version_id)
        REFERENCES model_config_version (id) ON DELETE CASCADE,
    CONSTRAINT chk_time_pricing_rule_days CHECK (days_mask BETWEEN 1 AND 127),
    CONSTRAINT chk_time_pricing_rule_time CHECK (start_time <> end_time),
    CONSTRAINT chk_time_pricing_rule_multiplier CHECK (quota_multiplier > 0),
    CONSTRAINT chk_time_pricing_rule_costs CHECK (
        (cost_override_enabled = FALSE
            AND input_cost_per_million IS NULL
            AND output_cost_per_million IS NULL
            AND cache_read_cost_per_million IS NULL
            AND cache_write_cost_per_million IS NULL)
        OR
        (cost_override_enabled = TRUE
            AND input_cost_per_million >= 0
            AND output_cost_per_million >= 0
            AND cache_read_cost_per_million >= 0
            AND cache_write_cost_per_million >= 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- V4 represented every day with one peak interval and an explicit off-peak
-- band. Preserve that behavior by promoting off-peak prices to the new
-- defaults and converting the old peak interval into one all-days rule.
UPDATE model_config_version
SET input_cost_per_million = off_peak_input_cost_per_million,
    output_cost_per_million = off_peak_output_cost_per_million,
    cache_read_cost_per_million = off_peak_cache_read_cost_per_million,
    cache_write_cost_per_million = off_peak_cache_write_cost_per_million
WHERE cost_time_pricing_enabled = TRUE;

INSERT INTO model_time_pricing_rule (
    id, version_id, rule_order, name, days_mask, start_time, end_time,
    cost_override_enabled, input_cost_per_million, output_cost_per_million,
    cache_read_cost_per_million, cache_write_cost_per_million, quota_multiplier
)
SELECT UUID(), id, 0, '峰时', 127, peak_start_time, peak_end_time,
       TRUE, peak_input_cost_per_million, peak_output_cost_per_million,
       peak_cache_read_cost_per_million, peak_cache_write_cost_per_million, 1
FROM model_config_version
WHERE cost_time_pricing_enabled = TRUE;

ALTER TABLE model_config_version
    DROP CHECK chk_model_version_time_pricing_range,
    DROP CHECK chk_model_version_time_pricing_costs;

ALTER TABLE model_config_version
    RENAME COLUMN cost_time_pricing_enabled TO time_pricing_enabled,
    RENAME COLUMN cost_time_zone TO time_pricing_zone,
    DROP COLUMN peak_start_time,
    DROP COLUMN peak_end_time,
    DROP COLUMN off_peak_input_cost_per_million,
    DROP COLUMN off_peak_output_cost_per_million,
    DROP COLUMN off_peak_cache_read_cost_per_million,
    DROP COLUMN off_peak_cache_write_cost_per_million,
    DROP COLUMN peak_input_cost_per_million,
    DROP COLUMN peak_output_cost_per_million,
    DROP COLUMN peak_cache_read_cost_per_million,
    DROP COLUMN peak_cache_write_cost_per_million,
    ADD CONSTRAINT chk_model_version_time_pricing CHECK (
        (time_pricing_enabled = FALSE AND time_pricing_zone IS NULL)
        OR (time_pricing_enabled = TRUE AND time_pricing_zone IS NOT NULL)
    ),
    ADD CONSTRAINT chk_model_version_default_multiplier CHECK (default_quota_multiplier > 0);
