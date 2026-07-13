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
