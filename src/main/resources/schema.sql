CREATE TABLE IF NOT EXISTS asset_category (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    color TEXT,
    created_date TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS asset_snapshot (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    snapshot_date TEXT NOT NULL,
    category_id INTEGER NOT NULL,
    amount NUMERIC NOT NULL,
    FOREIGN KEY (category_id) REFERENCES asset_category(id)
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

CREATE TABLE IF NOT EXISTS agent_conversation (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    provider_id TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_conversation_message (
    id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('user', 'assistant')),
    content TEXT NOT NULL,
    response_json TEXT,
    action_id TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (conversation_id) REFERENCES agent_conversation(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_agent_conversation_message_order
    ON agent_conversation_message(conversation_id, created_at, id);

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

CREATE TABLE IF NOT EXISTS ai_agent_permission (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    level TEXT NOT NULL CHECK (level IN ('READ_ONLY', 'REVIEW', 'DIRECT'))
);

INSERT OR IGNORE INTO ai_agent_permission (id, level) VALUES (1, 'REVIEW');
