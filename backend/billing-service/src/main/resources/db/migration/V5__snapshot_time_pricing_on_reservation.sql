ALTER TABLE billing_reservation
    ADD COLUMN pricing_at DATETIME(6) NULL AFTER pricing_version,
    ADD COLUMN quota_multiplier DECIMAL(12,6) NOT NULL DEFAULT 1 AFTER pricing_at,
    ADD COLUMN pricing_rule_name VARCHAR(80) NULL AFTER quota_multiplier;

UPDATE billing_reservation
SET pricing_at = created_at
WHERE pricing_at IS NULL;

ALTER TABLE billing_reservation
    MODIFY COLUMN pricing_at DATETIME(6) NOT NULL,
    ADD CONSTRAINT chk_reservation_quota_multiplier CHECK (quota_multiplier > 0);
