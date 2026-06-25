CREATE TABLE product_bolt (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    product_side_id       INTEGER NOT NULL,
    bolt_serial_num       INTEGER NOT NULL,
    bolt_name             TEXT,
    parameter_set_id      INTEGER,
    torque_min            REAL,
    torque_max            REAL,
    angle_min             REAL,
    angle_max             REAL,
    arm_location          TEXT,
    location_x_percent    REAL DEFAULT 0,
    location_y_percent    REAL DEFAULT 0,
    enabled               INTEGER DEFAULT 1,
    deleted               INTEGER DEFAULT 0,
    creator_id            INTEGER,
    modifier_id           INTEGER,
    create_time           TEXT,
    modify_time           TEXT
);

CREATE INDEX idx_product_bolt_product_side_id ON product_bolt(product_side_id);

CREATE TABLE bolt_device_binding (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    product_bolt_id       INTEGER NOT NULL,
    device_id             INTEGER NOT NULL,
    device_role           INTEGER NOT NULL,
    device_spec           REAL,
    sort_order            INTEGER DEFAULT 0,
    deleted               INTEGER DEFAULT 0,
    creator_id            INTEGER,
    modifier_id           INTEGER,
    create_time           TEXT,
    modify_time           TEXT
);

CREATE INDEX idx_bolt_device_binding_product_bolt_id ON bolt_device_binding(product_bolt_id);
CREATE INDEX idx_bolt_device_binding_device_id ON bolt_device_binding(device_id);

CREATE TABLE bolt_parts_barcode (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    product_bolt_id             INTEGER NOT NULL,
    bar_code_matching_rule_id   INTEGER NOT NULL,
    deleted                     INTEGER DEFAULT 0,
    creator_id                  INTEGER,
    modifier_id                  INTEGER,
    create_time                 TEXT,
    modify_time                 TEXT
);

CREATE INDEX idx_bolt_parts_barcode_product_bolt_id ON bolt_parts_barcode(product_bolt_id);
CREATE INDEX idx_bolt_parts_barcode_bar_code_matching_rule_id ON bolt_parts_barcode(bar_code_matching_rule_id);
