ALTER TABLE model_config_version
    ADD CONSTRAINT chk_model_version_billable_quota CHECK (
        input_quota_per_million > 0
        OR output_quota_per_million > 0
        OR reasoning_quota_per_million > 0
        OR cache_read_quota_per_million > 0
        OR cache_write_quota_per_million > 0
        OR minimum_request_quota > 0
    );
