ALTER TABLE equipment_instances ADD COLUMN item_level INTEGER NOT NULL DEFAULT 1;
ALTER TABLE equipment_instances ADD COLUMN quality INTEGER NOT NULL DEFAULT 100;
ALTER TABLE equipment_instances ADD COLUMN locked_affix_slots TEXT NOT NULL DEFAULT '';
ALTER TABLE equipment_instances ADD COLUMN reforge_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE equipment_instances ADD COLUMN reforge_history TEXT NOT NULL DEFAULT '';
