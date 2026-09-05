ALTER TABLE billing_reservation
    ADD COLUMN hold_released BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN reconciliation_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN reconciliation_checked_at DATETIME(3) NULL,
    ADD COLUMN reconciliation_next_at DATETIME(3) NULL,
    ADD COLUMN reconciliation_note VARCHAR(255) NULL,
    ADD INDEX idx_reservation_reconciliation (status, reconciliation_next_at, created_at);

-- Only the former expiry job's exact reason qualifies. Explicit releases remain terminal.
UPDATE billing_reservation
SET status = 'PENDING_RECONCILIATION', hold_released = TRUE,
    failure_reason = '历史预占已超时释放，等待用量核对'
WHERE status = 'RELEASED' AND failure_reason = '预占超时，系统自动释放';
