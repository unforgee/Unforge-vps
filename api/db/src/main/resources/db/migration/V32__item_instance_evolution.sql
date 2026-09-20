-- Item Instance Evolution 2.0 (phase 1): canonical snapshot columns, ownership/binding/state,
-- revision + fingerprint, lineage, append-only event log and the evolution record table.
-- All columns are additive with safe defaults; legacy rows remain valid.

ALTER TABLE equipment_instances ADD COLUMN instance_uuid TEXT NOT NULL DEFAULT '';
ALTER TABLE equipment_instances ADD COLUMN state TEXT NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE equipment_instances ADD COLUMN binding TEXT NOT NULL DEFAULT 'UNBOUND';
ALTER TABLE equipment_instances ADD COLUMN owner_account_id INTEGER;
ALTER TABLE equipment_instances ADD COLUMN owner_character_id INTEGER;
ALTER TABLE equipment_instances ADD COLUMN mastery_level INTEGER NOT NULL DEFAULT 0;
ALTER TABLE equipment_instances ADD COLUMN experience INTEGER NOT NULL DEFAULT 0;
ALTER TABLE equipment_instances ADD COLUMN evolution_stage INTEGER NOT NULL DEFAULT 0;
ALTER TABLE equipment_instances ADD COLUMN evolution_branch TEXT;
ALTER TABLE equipment_instances ADD COLUMN revision INTEGER NOT NULL DEFAULT 0;
ALTER TABLE equipment_instances ADD COLUMN fingerprint TEXT NOT NULL DEFAULT '';
ALTER TABLE equipment_instances ADD COLUMN lineage_parent_ids TEXT NOT NULL DEFAULT '';
ALTER TABLE equipment_instances ADD COLUMN lineage_recipe_id TEXT;
ALTER TABLE equipment_instances ADD COLUMN lineage_drop_id TEXT;
ALTER TABLE equipment_instances ADD COLUMN updated_at TIMESTAMP;

-- Deterministic UUID backfill derived from the primary key (valid v4-shaped, unique per row).
UPDATE equipment_instances
SET instance_uuid = printf('%08x-0000-4000-8000-%012x', id, id)
WHERE instance_uuid = '';

-- The legacy bound_character_id column becomes the new owner_character_id baseline.
UPDATE equipment_instances
SET owner_character_id = bound_character_id
WHERE bound_character_id IS NOT NULL AND owner_character_id IS NULL;

UPDATE equipment_instances SET updated_at = created_at WHERE updated_at IS NULL;

CREATE UNIQUE INDEX idx_equipment_instances_uuid
ON equipment_instances(instance_uuid);

CREATE INDEX idx_equipment_instances_owner_character
ON equipment_instances(owner_character_id);

CREATE INDEX idx_equipment_instances_owner_account
ON equipment_instances(owner_account_id);

CREATE INDEX idx_equipment_instances_state
ON equipment_instances(state);

CREATE INDEX idx_equipment_instances_binding
ON equipment_instances(binding);

CREATE INDEX idx_equipment_instances_fingerprint
ON equipment_instances(fingerprint);

CREATE INDEX idx_equipment_instances_template_rarity
ON equipment_instances(template_obj, rarity);

-- Evolution record: queryable stage/branch/milestone history per instance.
CREATE TABLE equipment_instance_evolution (
    equipment_instance_id INTEGER PRIMARY KEY,
    stage INTEGER NOT NULL DEFAULT 0,
    branch TEXT,
    unlocked_milestones TEXT NOT NULL DEFAULT '[]',
    last_evolved_at TIMESTAMP,
    FOREIGN KEY (equipment_instance_id)
        REFERENCES equipment_instances(id) ON DELETE CASCADE
);

-- Append-only mutation audit log with a tamper-evident hash chain.
CREATE TABLE equipment_instance_events (
    event_id TEXT PRIMARY KEY,
    equipment_instance_id INTEGER NOT NULL,
    operation TEXT NOT NULL,
    actor_type TEXT NOT NULL,
    actor_id INTEGER,
    source TEXT NOT NULL,
    before_revision INTEGER NOT NULL,
    after_revision INTEGER NOT NULL,
    before_fingerprint TEXT NOT NULL,
    after_fingerprint TEXT NOT NULL,
    payload TEXT NOT NULL DEFAULT '',
    previous_event_hash TEXT,
    event_hash TEXT NOT NULL,
    idempotency_key TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (equipment_instance_id)
        REFERENCES equipment_instances(id) ON DELETE CASCADE
);

CREATE INDEX idx_eie_instance_revision
ON equipment_instance_events(equipment_instance_id, after_revision);

CREATE INDEX idx_eie_created
ON equipment_instance_events(created_at);

-- Idempotency replays must find the first request for a key; one event per key per instance.
CREATE UNIQUE INDEX idx_eie_idempotency
ON equipment_instance_events(equipment_instance_id, idempotency_key)
WHERE idempotency_key IS NOT NULL;
