ALTER TABLE model_config_version
    ADD COLUMN supports_web_search BOOLEAN NOT NULL DEFAULT FALSE AFTER supports_json;
