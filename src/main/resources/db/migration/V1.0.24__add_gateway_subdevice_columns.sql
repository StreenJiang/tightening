ALTER TABLE device ADD COLUMN gateway_device_id INTEGER;
ALTER TABLE device ADD COLUMN arm_model_id INTEGER;

ALTER TABLE product_bolt ADD COLUMN arranger_device_id INTEGER;
ALTER TABLE product_bolt ADD COLUMN arranger_channels VARCHAR(50);
ALTER TABLE product_bolt ADD COLUMN setter_selector_id INTEGER;
ALTER TABLE product_bolt ADD COLUMN setter_position INTEGER;
