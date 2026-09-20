-- Companions may now have up to four active agents per owner (CompanionRules.ACTIVE_LIMIT = 4),
-- so the legacy "at most one active companion" partial unique index must go.
DROP INDEX IF EXISTS idx_companions_one_active;
