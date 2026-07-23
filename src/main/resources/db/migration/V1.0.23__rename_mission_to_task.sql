-- 表重命名（4 张）
ALTER TABLE product_mission RENAME TO product_task;
ALTER TABLE mission_record RENAME TO task_record;
ALTER TABLE mission_prerequisite RENAME TO task_prerequisite;
ALTER TABLE inspection_mission_binding RENAME TO inspection_task_binding;

-- 列重命名 — task_record
ALTER TABLE task_record RENAME COLUMN mission_result TO task_result;
ALTER TABLE task_record RENAME COLUMN product_mission_id TO product_task_id;

-- 列重命名 — task_prerequisite
ALTER TABLE task_prerequisite RENAME COLUMN mission_id TO task_id;
ALTER TABLE task_prerequisite RENAME COLUMN prerequisite_mission_id TO prerequisite_task_id;

-- 列重命名 — inspection_task_binding
ALTER TABLE inspection_task_binding RENAME COLUMN inspection_mission_id TO inspection_task_id;
ALTER TABLE inspection_task_binding RENAME COLUMN bound_mission_id TO bound_task_id;

-- 列重命名 — FK 关联列
ALTER TABLE product_side RENAME COLUMN product_mission_id TO product_task_id;
ALTER TABLE bar_code_matching_rule RENAME COLUMN product_mission_id TO product_task_id;
ALTER TABLE tightening_data RENAME COLUMN mission_record_id TO task_record_id;
ALTER TABLE curve_data RENAME COLUMN mission_record_id TO task_record_id;
ALTER TABLE export_task RENAME COLUMN mission_record_id TO task_record_id;

-- 索引重建（SQLite 不支持 RENAME INDEX，需 DROP + CREATE）
DROP INDEX IF EXISTS idx_product_mission_name;
CREATE UNIQUE INDEX idx_product_task_name ON product_task(name) WHERE deleted = 0;

DROP INDEX IF EXISTS idx_product_side_product_mission_id;
CREATE INDEX idx_product_side_product_task_id ON product_side(product_task_id);

DROP INDEX IF EXISTS idx_mission_prerequisite_mission_id;
CREATE INDEX idx_task_prerequisite_task_id ON task_prerequisite(task_id);

DROP INDEX IF EXISTS idx_mission_prerequisite_prerequisite_mission_id;
CREATE INDEX idx_task_prerequisite_prerequisite_task_id ON task_prerequisite(prerequisite_task_id);

DROP INDEX IF EXISTS idx_inspection_mission_binding_inspection_mission_id;
CREATE INDEX idx_inspection_task_binding_inspection_task_id ON inspection_task_binding(inspection_task_id);

DROP INDEX IF EXISTS idx_inspection_mission_binding_bound_mission_id;
CREATE INDEX idx_inspection_task_binding_bound_task_id ON inspection_task_binding(bound_task_id);

DROP INDEX IF EXISTS idx_bar_code_matching_rule_product_mission_id;
CREATE INDEX idx_bar_code_matching_rule_product_task_id ON bar_code_matching_rule(product_task_id);
