CREATE TABLE IF NOT EXISTS curve_data (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    mission_record_id INTEGER NOT NULL,
    workstation_name TEXT,
    product_side_name TEXT,
    bolt_serial_num INTEGER,
    parameter_set INTEGER,
    tightening_id INTEGER,
    timestamp TEXT,
    data_type INTEGER,
    data_samples TEXT,
    deleted INTEGER DEFAULT 0,
    creator_id INTEGER,
    modifier_id INTEGER,
    create_time TEXT,
    modify_time TEXT
);
