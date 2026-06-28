CREATE TABLE export_task (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    type              TEXT    NOT NULL,
    mission_record_id INTEGER NOT NULL,
    payload           TEXT    NOT NULL,
    status            INTEGER NOT NULL DEFAULT 0,
    retry_count       INTEGER NOT NULL DEFAULT 0,
    max_retries       INTEGER NOT NULL DEFAULT 3,
    error_message     TEXT,
    completed_at      TEXT,
    deleted           INTEGER DEFAULT 0,
    creator_id        INTEGER,
    modifier_id       INTEGER,
    create_time       TEXT,
    modify_time       TEXT
);
