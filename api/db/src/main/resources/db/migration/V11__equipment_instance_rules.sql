ALTER TABLE equipment_instances ADD COLUMN unique_effect_ids TEXT NOT NULL DEFAULT '';
ALTER TABLE equipment_instances ADD COLUMN item_tier INTEGER NOT NULL DEFAULT 1;

CREATE INDEX idx_equipment_instances_item_tier
ON equipment_instances(item_tier);
