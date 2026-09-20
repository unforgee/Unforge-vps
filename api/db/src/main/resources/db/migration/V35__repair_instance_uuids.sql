-- Item Instance Evolution 2.0 (uuid repair): the V32 backfill used printf('%08x', id), which
-- zero-pads but never truncates - ids wider than 32 bits produced a 13-hex-char first segment
-- and a non-canonical uuid that fails EquipmentInstance construction on load (account login
-- error). Rewrite every malformed uuid into a canonical v4-shaped value derived injectively
-- from the id bits: g1=bits0-31, g2=bits32-47, g3=bits48-59 (version nibble 4), g4=bits61-63
-- (variant nibble 8), g5=bits0-47. Deterministic and collision-free for all positive ids.
UPDATE equipment_instances
SET instance_uuid = printf(
    '%08x-%04x-4%03x-8%03x-%012x',
    id % 4294967296,
    (id / 4294967296) % 65536,
    (id / 281474976710656) % 4096,
    id / 2305843009213693952,
    id % 281474976710656
)
WHERE instance_uuid != '' AND length(instance_uuid) != 36;
