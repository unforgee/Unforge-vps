-- New accounts bypass Tutorial Island and begin in Edgeville.
-- CoordGrid: plane_region_x_region_y_local_x_local_y = 0_48_54_15_8 (3087, 3496, 0).
UPDATE realms
SET spawn_coord = '0_48_54_15_8',
    respawn_coord = '0_48_54_15_8'
WHERE name IN ('dev', 'main');

-- Repair older accounts as well. The login hook also enforces this at runtime.
UPDATE accounts SET members = 1;
