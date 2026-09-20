CREATE TABLE equipment_instance_skill_affixes (
    equipment_instance_id INTEGER NOT NULL,
    slot INTEGER NOT NULL,
    skill TEXT NOT NULL,
    effect TEXT NOT NULL,
    unit TEXT NOT NULL,
    magnitude INTEGER NOT NULL,
    PRIMARY KEY (equipment_instance_id, slot),
    FOREIGN KEY (equipment_instance_id) REFERENCES equipment_instances(id) ON DELETE CASCADE
);

CREATE INDEX idx_equipment_instance_skill_affixes_lookup
ON equipment_instance_skill_affixes(equipment_instance_id, skill, effect);
