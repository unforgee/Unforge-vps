ALTER TABLE inventory_objs
ADD COLUMN equipment_instance_id INTEGER NOT NULL DEFAULT 0;

CREATE TABLE equipment_instances (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    template_obj INTEGER NOT NULL,
    rarity TEXT NOT NULL,
    roll_seed INTEGER NOT NULL,
    schema_version INTEGER NOT NULL,
    balance_version INTEGER NOT NULL,
    source TEXT NOT NULL,
    bound_character_id INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (bound_character_id) REFERENCES characters(id) ON DELETE SET NULL
);

CREATE INDEX idx_equipment_instances_template
ON equipment_instances(template_obj);

CREATE INDEX idx_equipment_instances_bound_character
ON equipment_instances(bound_character_id);

CREATE TABLE equipment_instance_affixes (
    equipment_instance_id INTEGER NOT NULL,
    slot INTEGER NOT NULL,
    affix_id TEXT NOT NULL,
    magnitude INTEGER NOT NULL,
    flags INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (equipment_instance_id, slot),
    FOREIGN KEY (equipment_instance_id) REFERENCES equipment_instances(id) ON DELETE CASCADE
);

CREATE TABLE equipment_instance_sockets (
    equipment_instance_id INTEGER NOT NULL,
    slot INTEGER NOT NULL,
    socket_type TEXT NOT NULL,
    socketed_obj INTEGER,
    magnitude INTEGER NOT NULL DEFAULT 0,
    flags INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (equipment_instance_id, slot),
    FOREIGN KEY (equipment_instance_id) REFERENCES equipment_instances(id) ON DELETE CASCADE
);

CREATE TRIGGER inventory_objs_equipment_instance_template_insert
BEFORE INSERT ON inventory_objs
WHEN NEW.equipment_instance_id != 0
BEGIN
    SELECT CASE
        WHEN NOT EXISTS (
            SELECT 1
            FROM equipment_instances
            WHERE id = NEW.equipment_instance_id
              AND template_obj = NEW.obj
        )
        THEN RAISE(ABORT, 'equipment instance template mismatch')
    END;
END;

CREATE TRIGGER inventory_objs_equipment_instance_template_update
BEFORE UPDATE OF obj, equipment_instance_id ON inventory_objs
WHEN NEW.equipment_instance_id != 0
BEGIN
    SELECT CASE
        WHEN NOT EXISTS (
            SELECT 1
            FROM equipment_instances
            WHERE id = NEW.equipment_instance_id
              AND template_obj = NEW.obj
        )
        THEN RAISE(ABORT, 'equipment instance template mismatch')
    END;
END;
