-- Companion combat capability + spellbook/autocast state.
-- Defaults keep pre-feature companions loading unchanged: melee style, standard
-- spellbook and no autocast selection.
ALTER TABLE companions
    ADD COLUMN attack_style TEXT NOT NULL DEFAULT 'MELEE'
    CHECK (attack_style IN ('MELEE', 'RANGED', 'MAGIC'));

ALTER TABLE companions
    ADD COLUMN spellbook TEXT NOT NULL DEFAULT 'STANDARD'
    CHECK (spellbook IN ('STANDARD', 'ANCIENTS'));

-- The cache-backed autocast registry id of the selected spell. 0 = no autocast.
ALTER TABLE companions
    ADD COLUMN autocast_spell_id INTEGER NOT NULL DEFAULT 0
    CHECK (autocast_spell_id >= 0);
