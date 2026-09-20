CREATE TABLE companions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_character_id INTEGER NOT NULL,
    slot INTEGER NOT NULL CHECK (slot BETWEEN 1 AND 4),
    name TEXT NOT NULL,
    companion_class TEXT NOT NULL CHECK (companion_class IN ('TANK', 'SUPPORT', 'DPS')),
    npc_id INTEGER NOT NULL,
    level INTEGER NOT NULL DEFAULT 1,
    experience INTEGER NOT NULL DEFAULT 0,
    talent_points INTEGER NOT NULL DEFAULT 3,
    active INTEGER NOT NULL DEFAULT 0,
    state TEXT NOT NULL DEFAULT 'FOLLOWING',
    hitpoints INTEGER NOT NULL,
    maximum_hitpoints INTEGER NOT NULL,
    incapacitated_until INTEGER,
    encounter_id INTEGER,
    gear_instance_ids TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(owner_character_id, slot),
    FOREIGN KEY (owner_character_id) REFERENCES characters(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_companions_one_active ON companions(owner_character_id) WHERE active = 1;

CREATE TABLE companion_talents (
    companion_id INTEGER NOT NULL,
    talent_id TEXT NOT NULL,
    ranks INTEGER NOT NULL,
    PRIMARY KEY (companion_id, talent_id),
    FOREIGN KEY (companion_id) REFERENCES companions(id) ON DELETE CASCADE
);
