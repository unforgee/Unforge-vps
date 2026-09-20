-- Companion Evolve 2.0: evolution stage on companions and the five progression tracks
-- (MELEE/RANGED/MAGIC join SUPPORT/TANK) in companion_skill_xp. SQLite cannot alter a
-- CHECK constraint, so the table is rebuilt in place; existing rows are preserved.
ALTER TABLE companions ADD COLUMN evolution_stage INTEGER NOT NULL DEFAULT 0;

CREATE TABLE companion_skill_xp_new (
    companion_id INTEGER NOT NULL,
    owner_character_id INTEGER NOT NULL,
    skill TEXT NOT NULL CHECK (skill IN ('MELEE', 'RANGED', 'MAGIC', 'SUPPORT', 'TANK')),
    experience INTEGER NOT NULL DEFAULT 0 CHECK (experience >= 0),
    PRIMARY KEY (companion_id, skill),
    FOREIGN KEY (companion_id) REFERENCES companions(id) ON DELETE CASCADE
);

INSERT INTO companion_skill_xp_new (companion_id, owner_character_id, skill, experience)
    SELECT companion_id, owner_character_id, skill, experience FROM companion_skill_xp;

DROP TABLE companion_skill_xp;
ALTER TABLE companion_skill_xp_new RENAME TO companion_skill_xp;

CREATE INDEX idx_companion_skill_xp_owner ON companion_skill_xp(owner_character_id);
