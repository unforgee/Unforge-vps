-- Companion starter gear was historically stored with synthetic in-memory instance ids
-- (CompanionRuntimeScript used timestamp+random "dummy" ids that were never inserted into
-- equipment_instances). Those ids resolve to nothing after a restart, leaving companions
-- permanently gearless. Strip every gear_instance_ids entry that has no matching
-- equipment_instances row so only resolvable gear remains.
UPDATE companions
SET gear_instance_ids = (
    SELECT COALESCE(group_concat(j.value), '')
    FROM json_each('[' || companions.gear_instance_ids || ']') AS j
    WHERE j.value IN (SELECT id FROM equipment_instances)
)
WHERE gear_instance_ids IS NOT NULL AND gear_instance_ids != '';
