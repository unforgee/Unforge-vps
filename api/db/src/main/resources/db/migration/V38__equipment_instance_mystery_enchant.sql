-- Mystery Enchant is state on one item instance, never on the shared item template.
ALTER TABLE equipment_instances ADD COLUMN mystery_enchant_id TEXT;
ALTER TABLE equipment_instances ADD COLUMN mystery_enchant_tier TEXT;
ALTER TABLE equipment_instances ADD COLUMN mystery_enchant_kills_remaining INTEGER NOT NULL DEFAULT 0;
ALTER TABLE equipment_instances ADD COLUMN mystery_enchant_kills_max INTEGER NOT NULL DEFAULT 0;
