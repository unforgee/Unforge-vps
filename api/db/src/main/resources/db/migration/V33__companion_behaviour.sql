-- Companion Evolve 2.0 Behaviour tab: persisted AI configuration. Single TEXT column encoded as
-- `TARGET_PRIORITY;followDistance;autoTaunt;autoDefend;autoHeal;healBelowPercent;autoBuff;catchUp`
-- (bools as 0/1). Rows predating the column deserialize to the safe defaults.
ALTER TABLE companions ADD COLUMN behaviour TEXT NOT NULL DEFAULT 'OWNER_TARGET;2;1;1;1;80;1;1';
