ALTER TABLE model_config_version
    ADD COLUMN cost_time_pricing_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER cache_write_cost_per_million,
    ADD COLUMN cost_time_zone VARCHAR(64) NULL AFTER cost_time_pricing_enabled,
    ADD COLUMN peak_start_time TIME NULL AFTER cost_time_zone,
    ADD COLUMN peak_end_time TIME NULL AFTER peak_start_time,
    ADD COLUMN off_peak_input_cost_per_million DECIMAL(20,6) NOT NULL DEFAULT 0 AFTER peak_end_time,
    ADD COLUMN off_peak_output_cost_per_million DECIMAL(20,6) NOT NULL DEFAULT 0 AFTER off_peak_input_cost_per_million,
    ADD COLUMN off_peak_cache_read_cost_per_million DECIMAL(20,6) NOT NULL DEFAULT 0 AFTER off_peak_output_cost_per_million,
    ADD COLUMN off_peak_cache_write_cost_per_million DECIMAL(20,6) NOT NULL DEFAULT 0 AFTER off_peak_cache_read_cost_per_million,
    ADD COLUMN peak_input_cost_per_million DECIMAL(20,6) NOT NULL DEFAULT 0 AFTER off_peak_cache_write_cost_per_million,
    ADD COLUMN peak_output_cost_per_million DECIMAL(20,6) NOT NULL DEFAULT 0 AFTER peak_input_cost_per_million,
    ADD COLUMN peak_cache_read_cost_per_million DECIMAL(20,6) NOT NULL DEFAULT 0 AFTER peak_output_cost_per_million,
    ADD COLUMN peak_cache_write_cost_per_million DECIMAL(20,6) NOT NULL DEFAULT 0 AFTER peak_cache_read_cost_per_million;

-- Reasoning tokens are a detail of provider output usage, not an independent
-- billing category. Preserve the more conservative historical rate and then
-- retire the separate rate columns without dropping them from an existing DB.
UPDATE model_config_version
SET output_cost_per_million = GREATEST(output_cost_per_million, reasoning_cost_per_million),
    output_quota_per_million = GREATEST(output_quota_per_million, reasoning_quota_per_million),
    reasoning_cost_per_million = 0,
    reasoning_quota_per_million = 0;

ALTER TABLE model_config_version
    ADD CONSTRAINT chk_model_version_time_pricing_range CHECK (
        (cost_time_pricing_enabled = FALSE
            AND cost_time_zone IS NULL AND peak_start_time IS NULL AND peak_end_time IS NULL)
        OR
        (cost_time_pricing_enabled = TRUE
            AND cost_time_zone IS NOT NULL
            AND peak_start_time IS NOT NULL AND peak_end_time IS NOT NULL
            AND peak_start_time <> peak_end_time)
    ),
    ADD CONSTRAINT chk_model_version_time_pricing_costs CHECK (
        off_peak_input_cost_per_million >= 0
        AND off_peak_output_cost_per_million >= 0
        AND off_peak_cache_read_cost_per_million >= 0
        AND off_peak_cache_write_cost_per_million >= 0
        AND peak_input_cost_per_million >= 0
        AND peak_output_cost_per_million >= 0
        AND peak_cache_read_cost_per_million >= 0
        AND peak_cache_write_cost_per_million >= 0
    );
