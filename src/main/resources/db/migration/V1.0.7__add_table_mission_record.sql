CREATE TABLE mission_record (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    product_mission_id  INTEGER,
    product_code        TEXT,
    is_rework           INTEGER DEFAULT 0,
    mission_result      INTEGER,
    deleted             INTEGER DEFAULT 0,
    creator_id          INTEGER,
    modifier_id         INTEGER,
    create_time         TEXT,
    modify_time         TEXT
);
