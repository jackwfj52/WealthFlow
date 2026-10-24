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

CREATE TABLE IF NOT EXISTS asset_holding (
    id TEXT PRIMARY KEY,
    category_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    weight_bps INTEGER NOT NULL CHECK (weight_bps > 0 AND weight_bps <= 10000),
    sector TEXT,
    updated_at TEXT,
    position INTEGER NOT NULL,
    UNIQUE (category_id, name COLLATE NOCASE),
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

CREATE TABLE IF NOT EXISTS knowledge_document (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    type TEXT NOT NULL,
    format TEXT NOT NULL,
    body TEXT NOT NULL,
    tags_json TEXT NOT NULL DEFAULT '[]',
    status TEXT NOT NULL,
    version INTEGER NOT NULL,
    content_hash TEXT NOT NULL,
    source_name TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_knowledge_document_status_type ON knowledge_document(status, type);
CREATE INDEX IF NOT EXISTS idx_knowledge_document_hash_type ON knowledge_document(content_hash, type);
CREATE TABLE IF NOT EXISTS knowledge_version (
    document_id TEXT NOT NULL,
    version INTEGER NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    format TEXT NOT NULL,
    tags_json TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY(document_id, version)
);
CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    document_id TEXT NOT NULL,
    ordinal INTEGER NOT NULL,
    section TEXT NOT NULL,
    content TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    embedding_status TEXT NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_retry_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_document ON knowledge_chunk(document_id, ordinal);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_pending ON knowledge_chunk(embedding_status, next_retry_at);
CREATE VIRTUAL TABLE IF NOT EXISTS knowledge_fts USING fts5(title, tags, content, tokenize='trigram');
CREATE TABLE IF NOT EXISTS knowledge_embedding (
    chunk_id INTEGER PRIMARY KEY,
    vector_blob BLOB NOT NULL,
    dimensions INTEGER NOT NULL,
    fingerprint TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS knowledge_embedding_config (
    id INTEGER PRIMARY KEY CHECK(id=1),
    mode TEXT NOT NULL,
    provider_id TEXT,
    base_url TEXT,
    model TEXT NOT NULL,
    encrypted_api_key TEXT,
    enabled INTEGER NOT NULL DEFAULT 0,
    fingerprint TEXT,
    updated_at TEXT NOT NULL
);
