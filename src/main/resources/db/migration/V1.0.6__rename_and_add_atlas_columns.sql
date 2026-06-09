ALTER TABLE tightening_data RENAME COLUMN tightening_result TO tightening_status;
ALTER TABLE tightening_data RENAME COLUMN torque_result TO torque_status;
ALTER TABLE tightening_data RENAME COLUMN angle_result TO angle_status;
ALTER TABLE tightening_data RENAME COLUMN rundown_angle_result TO rundown_angle_status;

ALTER TABLE tightening_data ADD COLUMN cell_id INTEGER;
ALTER TABLE tightening_data ADD COLUMN channel_id INTEGER;
ALTER TABLE tightening_data ADD COLUMN controller_name TEXT;
ALTER TABLE tightening_data ADD COLUMN vin TEXT;
ALTER TABLE tightening_data ADD COLUMN job_id INTEGER;
ALTER TABLE tightening_data ADD COLUMN batch_size INTEGER;
ALTER TABLE tightening_data ADD COLUMN batch_counter INTEGER;
ALTER TABLE tightening_data ADD COLUMN batch_status INTEGER;
ALTER TABLE tightening_data ADD COLUMN revision INTEGER;
ALTER TABLE tightening_data ADD COLUMN extra_data TEXT;
