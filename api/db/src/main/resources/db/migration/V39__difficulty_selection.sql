-- Server-authoritative difficulty tier, stored on the existing `characters` row.
--
-- `difficulty` is NULL until the account completes the first-login difficulty selection
-- (`difficulty_selected = 0`). Unlike the V19 game-mode backfill, existing characters are
-- intentionally NOT backfilled: every account picks a tier once on next login so the
-- feature applies uniformly.
--
-- The tier owns the personal xp rate (`xp_rate_in_hundreds`), so the realm-wide
-- `global_xp_rate` returns to its intended role as a temporary event multiplier
-- (e.g. double-xp weekends) and resets to 1.0 for all realms.
ALTER TABLE characters ADD COLUMN difficulty TEXT;
ALTER TABLE characters ADD COLUMN difficulty_selected INTEGER NOT NULL DEFAULT 0;
ALTER TABLE characters ADD COLUMN difficulty_selected_at TIMESTAMP;

UPDATE realms SET global_xp_rate_in_hundreds = 100;
