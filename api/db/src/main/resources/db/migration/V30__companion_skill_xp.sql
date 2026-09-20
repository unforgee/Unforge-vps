CREATE TABLE companion_skill_xp (
    companion_id INTEGER NOT NULL,
    owner_character_id INTEGER NOT NULL,
    skill TEXT NOT NULL CHECK (skill IN ('SUPPORT', 'TANK')),
    experience INTEGER NOT NULL DEFAULT 0 CHECK (experience >= 0),
    PRIMARY KEY (companion_id, skill),
    FOREIGN KEY (companion_id) REFERENCES companions(id) ON DELETE CASCADE
);

CREATE INDEX idx_companion_skill_xp_owner ON companion_skill_xp(owner_character_id);
