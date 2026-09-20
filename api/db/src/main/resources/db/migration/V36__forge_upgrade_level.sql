-- Forge 2.0: persisted upgrade level ("+N") for equipment instances.
-- Additive with a safe default; existing rows keep working with upgrade_level = 0.

ALTER TABLE equipment_instances ADD COLUMN upgrade_level INTEGER NOT NULL DEFAULT 0;

-- Legacy Forge upgrades were tracked as repeated 'forge-upgrade' markers in reforge_history
-- (capped at 10 by the new Forge cap). Backfill the column so previously upgraded items
-- keep displaying their level.
UPDATE equipment_instances
SET upgrade_level = MIN(10,
    (LENGTH(reforge_history) - LENGTH(REPLACE(reforge_history, 'forge-upgrade', ''))) / 13)
WHERE reforge_history LIKE '%forge-upgrade%';
