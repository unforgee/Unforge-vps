-- Item Instance Evolution 2.0 (restore slice): each audit event may carry the canonical
-- post-mutation snapshot so `restoreFromEvent` can rebuild the exact historical state.
-- NULL for events written before this column existed - those events remain valid audit
-- records but are deliberately not restorable (fail-closed, never guessed).
ALTER TABLE equipment_instance_events ADD COLUMN snapshot TEXT;
