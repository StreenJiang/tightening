-- Make mission_record_id nullable in tightening_data (SQLite requires table recreation)
ALTER TABLE tightening_data RENAME TO tightening_data_old;

CREATE TABLE tightening_data (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    deleted INTEGER,
    creator_id INTEGER,
    modifier_id INTEGER,
    create_time TEXT,
    modify_time TEXT,
    mission_record_id INTEGER,
    workstation_name TEXT,
    tool_name TEXT,
    tool_type_name TEXT,
    product_side_name TEXT,
    bolt_serial_num INTEGER,
    arm_location TEXT,
    parameter_set INTEGER,
    parameter_set_name TEXT,
    tightening_id INTEGER,
    tightening_status INTEGER,
    result_type INTEGER,
    torque_status INTEGER,
    angle_status INTEGER,
    rundown_angle_status INTEGER,
    torque_values_unit INTEGER,
    torque_min_limit REAL,
    torque_max_limit REAL,
    torque_final_target REAL,
    torque REAL,
    angle_min_limit REAL,
    angle_max_limit REAL,
    angle_final_target REAL,
    angle REAL,
    rundown_angle_min_limit REAL,
    rundown_angle_max_limit REAL,
    rundown_angle REAL,
    timestamp TEXT,
    cell_id INTEGER,
    channel_id INTEGER,
    controller_name TEXT,
    vin TEXT,
    job_id INTEGER,
    batch_size INTEGER,
    batch_counter INTEGER,
    batch_status INTEGER,
    revision INTEGER,
    extra_data TEXT
);

INSERT INTO tightening_data SELECT * FROM tightening_data_old;
DROP TABLE tightening_data_old;

-- Make mission_record_id nullable in curve_data
ALTER TABLE curve_data RENAME TO curve_data_old;

CREATE TABLE curve_data (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    mission_record_id INTEGER,
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

INSERT INTO curve_data SELECT * FROM curve_data_old;
DROP TABLE curve_data_old;
