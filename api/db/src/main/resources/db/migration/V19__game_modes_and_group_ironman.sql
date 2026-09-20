-- Server-authoritative game mode (ironman) state, stored on the existing `characters` row.
--
-- `game_mode` is NULL only for accounts that have not yet completed the first-login
-- selection (`game_mode_selected = 0`). New characters inherit the column defaults below,
-- so they start unselected. Existing characters are backfilled to REGULAR/selected so they
-- are never forced through the selection flow.

ALTER TABLE characters ADD COLUMN game_mode TEXT;
ALTER TABLE characters ADD COLUMN game_mode_selected INTEGER NOT NULL DEFAULT 0;
ALTER TABLE characters ADD COLUMN game_mode_selected_at TIMESTAMP;
ALTER TABLE characters ADD COLUMN hardcore_status TEXT NOT NULL DEFAULT 'DISABLED';
ALTER TABLE characters ADD COLUMN hardcore_death_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE characters ADD COLUMN group_id INTEGER;
ALTER TABLE characters ADD COLUMN group_rank TEXT;
ALTER TABLE characters ADD COLUMN group_joined_at TIMESTAMP;
ALTER TABLE characters ADD COLUMN group_settings_version INTEGER NOT NULL DEFAULT 0;

-- Migration: every pre-existing character is a legacy account and defaults to REGULAR.
UPDATE characters
SET game_mode = 'REGULAR',
    game_mode_selected = 1,
    game_mode_selected_at = CURRENT_TIMESTAMP
WHERE game_mode IS NULL;

CREATE TABLE ironman_groups (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    realm_id INTEGER NOT NULL,
    name TEXT,
    leader_character_id INTEGER,
    settings_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (realm_id) REFERENCES realms(id)
);

-- Shared group storage contents. One row per occupied slot.
CREATE TABLE ironman_group_storage (
    group_id INTEGER NOT NULL,
    slot INTEGER NOT NULL,
    obj INTEGER NOT NULL,
    count INTEGER NOT NULL,
    vars INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (group_id, slot),
    FOREIGN KEY (group_id) REFERENCES ironman_groups(id) ON DELETE CASCADE
);

-- Every mutation of group storage (or membership) is recorded here.
CREATE TABLE ironman_group_audit (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    group_id INTEGER NOT NULL,
    character_id INTEGER NOT NULL,
    action TEXT NOT NULL,
    obj INTEGER,
    count INTEGER,
    detail TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ironman_group_audit_group ON ironman_group_audit(group_id);

-- Every admin-driven game mode change is recorded here.
CREATE TABLE game_mode_audit (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    character_id INTEGER NOT NULL,
    actor_character_id INTEGER,
    old_mode TEXT,
    new_mode TEXT NOT NULL,
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_game_mode_audit_character ON game_mode_audit(character_id);
