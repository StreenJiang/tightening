CREATE TABLE arm_model_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(20) NOT NULL,
    x_slave_addr INTEGER NOT NULL,
    x_register INTEGER NOT NULL,
    x_count INTEGER NOT NULL DEFAULT 1,
    y_slave_addr INTEGER NOT NULL,
    y_register INTEGER NOT NULL,
    y_count INTEGER NOT NULL DEFAULT 1,
    z_slave_addr INTEGER,
    z_register INTEGER,
    z_count INTEGER DEFAULT 1,
    parse_strategy VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    deleted INTEGER DEFAULT 0,
    creator_id INTEGER,
    modifier_id INTEGER,
    create_time TEXT,
    modify_time TEXT
);

INSERT INTO arm_model_config (id, name, x_slave_addr, x_register, x_count,
    y_slave_addr, y_register, y_count, z_slave_addr, z_register, z_count, parse_strategy)
VALUES
  (1, 'CF01', 1, 0x0003, 2, 2, 0x0003, 2, NULL, NULL, 0, 'STANDARD'),
  (2, 'CF02', 1, 0x0000, 1, 2, 0x0000, 1, NULL, NULL, 0, 'STANDARD'),
  (3, 'CF03', 1, 0x0000, 1, 2, 0x0000, 1, 3, 0x0000, 1, 'STANDARD'),
  (4, 'CF04', 1, 0x0019, 1, 2, 0x0019, 1, NULL, NULL, 0, 'DIVIDE_BY_100');
