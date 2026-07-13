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