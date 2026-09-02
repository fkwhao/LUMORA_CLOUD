ALTER TABLE billing_plan_version
    ADD COLUMN model_access_mode VARCHAR(24) NOT NULL DEFAULT 'ALL_PUBLISHED_LEGACY' AFTER weekly_quota;

CREATE TABLE billing_plan_version_model (
    plan_version_id BIGINT UNSIGNED NOT NULL,
    model_code VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_order INT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (plan_version_id, model_code),
    KEY idx_plan_version_model_code (model_code, plan_version_id),
    UNIQUE KEY uk_plan_version_model_order (plan_version_id, display_order),
    CONSTRAINT fk_plan_version_model_version
        FOREIGN KEY (plan_version_id) REFERENCES billing_plan_version (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
