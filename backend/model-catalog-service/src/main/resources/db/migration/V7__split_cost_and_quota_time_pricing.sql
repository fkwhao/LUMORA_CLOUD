ALTER TABLE model_config_version
    ADD COLUMN cost_time_pricing_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER cache_write_cost_per_million,
    ADD COLUMN cost_time_pricing_zone VARCHAR(64) NULL AFTER cost_time_pricing_enabled,
    ADD COLUMN quota_time_pricing_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER minimum_request_quota,
    ADD COLUMN quota_time_pricing_zone VARCHAR(64) NULL AFTER quota_time_pricing_enabled;

-- Preserve the old combined policy. Cost rules only exist where an explicit
-- supplier cost override was configured; every old rule carried a quota
-- multiplier, so the old policy becomes the quota policy as-is.
UPDATE model_config_version version
SET cost_time_pricing_enabled = EXISTS (
        SELECT 1
        FROM model_time_pricing_rule rule
        WHERE rule.version_id = version.id AND rule.cost_override_enabled = TRUE
    ),
    cost_time_pricing_zone = CASE
        WHEN EXISTS (
            SELECT 1
            FROM model_time_pricing_rule rule
            WHERE rule.version_id = version.id AND rule.cost_override_enabled = TRUE
        ) THEN version.time_pricing_zone
        ELSE NULL
    END,
    quota_time_pricing_enabled = version.time_pricing_enabled,
    quota_time_pricing_zone = CASE
        WHEN version.time_pricing_enabled = TRUE THEN version.time_pricing_zone
        ELSE NULL
    END;

ALTER TABLE model_time_pricing_rule
    DROP INDEX uk_time_pricing_rule_order,
    DROP CHECK chk_time_pricing_rule_costs,
    DROP CHECK chk_time_pricing_rule_multiplier,
    ADD COLUMN pricing_scope VARCHAR(8) NULL AFTER version_id,
    MODIFY COLUMN quota_multiplier DECIMAL(12,6) NULL;

INSERT INTO model_time_pricing_rule (
    id, version_id, pricing_scope, rule_order, name, days_mask, start_time, end_time,
    cost_override_enabled, input_cost_per_million, output_cost_per_million,
    cache_read_cost_per_million, cache_write_cost_per_million, quota_multiplier
)
SELECT UUID(), version_id, 'COST', rule_order, name, days_mask, start_time, end_time,
       TRUE, input_cost_per_million, output_cost_per_million,
       cache_read_cost_per_million, cache_write_cost_per_million, NULL
FROM model_time_pricing_rule
WHERE cost_override_enabled = TRUE;

UPDATE model_time_pricing_rule
SET pricing_scope = 'QUOTA',
    cost_override_enabled = FALSE,
    input_cost_per_million = NULL,
    output_cost_per_million = NULL,
    cache_read_cost_per_million = NULL,
    cache_write_cost_per_million = NULL
WHERE pricing_scope IS NULL;

ALTER TABLE model_time_pricing_rule
    DROP COLUMN cost_override_enabled,
    MODIFY COLUMN pricing_scope VARCHAR(8) NOT NULL,
    ADD UNIQUE KEY uk_time_pricing_rule_scope_order (version_id, pricing_scope, rule_order),
    ADD CONSTRAINT chk_time_pricing_rule_scope CHECK (
        (pricing_scope = 'COST'
            AND input_cost_per_million IS NOT NULL
            AND output_cost_per_million IS NOT NULL
            AND cache_read_cost_per_million IS NOT NULL
            AND cache_write_cost_per_million IS NOT NULL
            AND input_cost_per_million >= 0
            AND output_cost_per_million >= 0
            AND cache_read_cost_per_million >= 0
            AND cache_write_cost_per_million >= 0
            AND quota_multiplier IS NULL)
        OR
        (pricing_scope = 'QUOTA'
            AND input_cost_per_million IS NULL
            AND output_cost_per_million IS NULL
            AND cache_read_cost_per_million IS NULL
            AND cache_write_cost_per_million IS NULL
            AND quota_multiplier IS NOT NULL
            AND quota_multiplier > 0)
    );

ALTER TABLE model_config_version
    DROP CHECK chk_model_version_time_pricing,
    DROP COLUMN time_pricing_enabled,
    DROP COLUMN time_pricing_zone,
    ADD CONSTRAINT chk_model_version_cost_time_pricing CHECK (
        (cost_time_pricing_enabled = FALSE AND cost_time_pricing_zone IS NULL)
        OR (cost_time_pricing_enabled = TRUE AND cost_time_pricing_zone IS NOT NULL)
    ),
    ADD CONSTRAINT chk_model_version_quota_time_pricing CHECK (
        (quota_time_pricing_enabled = FALSE AND quota_time_pricing_zone IS NULL)
        OR (quota_time_pricing_enabled = TRUE AND quota_time_pricing_zone IS NOT NULL)
    );
