-- Companion pack storage capacity (10 to 100 slots, base 10).
ALTER TABLE companions
    ADD COLUMN pack_capacity INTEGER NOT NULL DEFAULT 10
    CHECK (pack_capacity BETWEEN 10 AND 100);

