CREATE TABLE product_side (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    product_mission_id    INTEGER NOT NULL,
    name                  TEXT,
    image_data            BLOB,
    rendered_image_data   BLOB,
    thumbnail_data        BLOB,
    deleted               INTEGER DEFAULT 0,
    creator_id            INTEGER,
    modifier_id           INTEGER,
    create_time           TEXT,
    modify_time           TEXT
);

CREATE INDEX idx_product_side_product_mission_id ON product_side(product_mission_id);
