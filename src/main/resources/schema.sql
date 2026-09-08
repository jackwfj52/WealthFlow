CREATE TABLE IF NOT EXISTS asset_category (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    created_date TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS asset_snapshot (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    snapshot_date TEXT NOT NULL,
    category_id INTEGER NOT NULL,
    amount NUMERIC NOT NULL,
    FOREIGN KEY (category_id) REFERENCES asset_category(id)
);

INSERT INTO asset_category (name, created_date)
SELECT '股票', CURRENT_DATE
WHERE NOT EXISTS (
    SELECT 1
    FROM asset_category
    WHERE name = '股票'
);

INSERT INTO asset_category (name, created_date)
SELECT '现金', CURRENT_DATE
WHERE NOT EXISTS (
    SELECT 1
    FROM asset_category
    WHERE name = '现金'
);

CREATE TABLE IF NOT EXISTS pending_action (
    id TEXT PRIMARY KEY,
    action_type TEXT NOT NULL,
    status TEXT NOT NULL,
    payload_json TEXT NOT NULL,
    display_summary TEXT NOT NULL,
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    executed_at TEXT,
    failure_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_pending_action_status_expires_at
    ON pending_action(status, expires_at);

CREATE TABLE IF NOT EXISTS ai_provider_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    provider_id TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    protocol TEXT NOT NULL,
    base_url TEXT NOT NULL,
    model TEXT NOT NULL,
    encrypted_api_key TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);