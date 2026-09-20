ALTER TABLE equipment_instances ADD COLUMN category TEXT NOT NULL DEFAULT 'Custom';
ALTER TABLE equipment_instances ADD COLUMN unique_effect_id TEXT;

CREATE INDEX idx_equipment_instances_category
ON equipment_instances(category);

CREATE INDEX idx_equipment_instances_unique_effect
ON equipment_instances(unique_effect_id);
