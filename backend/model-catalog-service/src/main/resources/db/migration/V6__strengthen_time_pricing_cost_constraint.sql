ALTER TABLE model_time_pricing_rule
    DROP CHECK chk_time_pricing_rule_costs,
    ADD CONSTRAINT chk_time_pricing_rule_costs CHECK (
        (cost_override_enabled = FALSE
            AND input_cost_per_million IS NULL
            AND output_cost_per_million IS NULL
            AND cache_read_cost_per_million IS NULL
            AND cache_write_cost_per_million IS NULL)
        OR
        (cost_override_enabled = TRUE
            AND input_cost_per_million IS NOT NULL
            AND output_cost_per_million IS NOT NULL
            AND cache_read_cost_per_million IS NOT NULL
            AND cache_write_cost_per_million IS NOT NULL
            AND input_cost_per_million >= 0
            AND output_cost_per_million >= 0
            AND cache_read_cost_per_million >= 0
            AND cache_write_cost_per_million >= 0)
    );
