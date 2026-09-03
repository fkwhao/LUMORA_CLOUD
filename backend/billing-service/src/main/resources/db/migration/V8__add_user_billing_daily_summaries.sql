CREATE TABLE billing_usage_daily_summary (
    user_id BIGINT UNSIGNED NOT NULL,
    summary_date DATE NOT NULL,
    request_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    completed_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    pending_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    failed_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    input_tokens BIGINT UNSIGNED NOT NULL DEFAULT 0,
    output_tokens BIGINT UNSIGNED NOT NULL DEFAULT 0,
    reasoning_tokens BIGINT UNSIGNED NOT NULL DEFAULT 0,
    cache_read_tokens BIGINT UNSIGNED NOT NULL DEFAULT 0,
    cache_write_tokens BIGINT UNSIGNED NOT NULL DEFAULT 0,
    billed_quota DECIMAL(20,6) UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, summary_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO billing_usage_daily_summary (
    user_id, summary_date, request_count, completed_count, pending_count, failed_count,
    input_tokens, output_tokens, reasoning_tokens, cache_read_tokens, cache_write_tokens, billed_quota
)
SELECT
    user_id,
    DATE(occurred_at),
    COUNT(*),
    SUM(status = 'COMPLETED'),
    SUM(status IN ('PROCESSING', 'PENDING_RECONCILIATION')),
    SUM(status = 'FAILED'),
    COALESCE(SUM(input_tokens), 0),
    COALESCE(SUM(output_tokens), 0),
    COALESCE(SUM(reasoning_tokens), 0),
    COALESCE(SUM(cache_read_tokens), 0),
    COALESCE(SUM(cache_write_tokens), 0),
    COALESCE(SUM(billed_quota), 0)
FROM billing_usage_record
GROUP BY user_id, DATE(occurred_at);

CREATE TABLE billing_quota_daily_summary (
    user_id BIGINT UNSIGNED NOT NULL,
    summary_date DATE NOT NULL,
    entry_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    granted_delta DECIMAL(20,6) NOT NULL DEFAULT 0,
    reserved_delta DECIMAL(20,6) NOT NULL DEFAULT 0,
    consumed_delta DECIMAL(20,6) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, summary_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO billing_quota_daily_summary (
    user_id, summary_date, entry_count, granted_delta, reserved_delta, consumed_delta
)
SELECT
    user_id,
    DATE(created_at),
    COUNT(*),
    COALESCE(SUM(granted_delta), 0),
    COALESCE(SUM(reserved_delta), 0),
    COALESCE(SUM(consumed_delta), 0)
FROM quota_ledger
GROUP BY user_id, DATE(created_at);
