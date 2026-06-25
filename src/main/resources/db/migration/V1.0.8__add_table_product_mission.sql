CREATE TABLE product_mission (
    id                            INTEGER PRIMARY KEY AUTOINCREMENT,
    name                          TEXT,
    max_ng_count                  INTEGER,
    password_required_after_ng    INTEGER,
    enabled                       INTEGER DEFAULT 1,
    multi_device_independent      INTEGER DEFAULT 0,
    skip_screw                    INTEGER DEFAULT 0,
    is_inspection                 INTEGER DEFAULT 0,
    inspection_scope              INTEGER,
    deleted                       INTEGER DEFAULT 0,
    creator_id                    INTEGER,
    modifier_id                   INTEGER,
    create_time                   TEXT,
    modify_time                   TEXT
);

CREATE TABLE mission_prerequisite (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    mission_id                  INTEGER NOT NULL,
    prerequisite_mission_id     INTEGER NOT NULL,
    prerequisite_type           INTEGER NOT NULL,
    deleted                     INTEGER DEFAULT 0,
    creator_id                  INTEGER,
    modifier_id                 INTEGER,
    create_time                 TEXT,
    modify_time                 TEXT
);

CREATE INDEX idx_mission_prerequisite_mission_id ON mission_prerequisite(mission_id);
CREATE INDEX idx_mission_prerequisite_prerequisite_mission_id ON mission_prerequisite(prerequisite_mission_id);

CREATE TABLE inspection_mission_binding (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    inspection_mission_id       INTEGER NOT NULL,
    bound_mission_id            INTEGER NOT NULL,
    deleted                     INTEGER DEFAULT 0,
    creator_id                  INTEGER,
    modifier_id                 INTEGER,
    create_time                 TEXT,
    modify_time                 TEXT
);

CREATE INDEX idx_inspection_mission_binding_inspection_mission_id ON inspection_mission_binding(inspection_mission_id);
CREATE INDEX idx_inspection_mission_binding_bound_mission_id ON inspection_mission_binding(bound_mission_id);

CREATE TABLE bar_code_matching_rule (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    name                  TEXT,
    product_mission_id    INTEGER,
    rule_type             INTEGER NOT NULL,
    part_number           TEXT,
    expected_length       INTEGER,
    key_start_position    INTEGER,
    key_end_position      INTEGER,
    key_char              TEXT,
    deleted               INTEGER DEFAULT 0,
    creator_id            INTEGER,
    modifier_id           INTEGER,
    create_time           TEXT,
    modify_time           TEXT
);

CREATE INDEX idx_bar_code_matching_rule_product_mission_id ON bar_code_matching_rule(product_mission_id);
