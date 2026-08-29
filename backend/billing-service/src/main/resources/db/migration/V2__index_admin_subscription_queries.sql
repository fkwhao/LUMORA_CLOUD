CREATE INDEX idx_subscription_created
    ON billing_subscription (created_at, id);
