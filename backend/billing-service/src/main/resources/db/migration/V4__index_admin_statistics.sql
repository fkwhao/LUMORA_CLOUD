CREATE INDEX idx_subscription_status_time
    ON billing_subscription (status, starts_at, ends_at);

CREATE INDEX idx_purchase_order_status_paid
    ON billing_purchase_order (status, paid_at);

CREATE INDEX idx_usage_record_occurred_status
    ON billing_usage_record (occurred_at, status);
