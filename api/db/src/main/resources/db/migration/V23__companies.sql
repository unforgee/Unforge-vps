-- Company (clan/team) talent-tree state.
--
-- Membership lives in `company_members` (one row per character - the PK enforces single
-- membership); the shared tree's unspent points live on the `companies` row and trained ranks
-- in `company_talents`. Company state is cached in memory by `CompanyService` and flushed
-- inside a member's character save transaction, so ranks/points persist atomically with the
-- member who triggered the save - the same contract `ironman_group_storage` uses.
--
-- Membership rows are written immediately on the db thread for structural changes
-- (create/join/leave/kick/dissolve), so a member's permissions are never stale even before
-- their next character save.

CREATE TABLE companies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    realm_id INTEGER NOT NULL,
    name TEXT,
    leader_character_id INTEGER,
    talent_points INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (realm_id) REFERENCES realms(id)
);

-- One row per member; `character_id` is the PK so a character can belong to at most one
-- company. Role is LEADER or MEMBER - `leaderOnly` talents require LEADER to train.
CREATE TABLE company_members (
    character_id INTEGER PRIMARY KEY,
    company_id INTEGER NOT NULL,
    role TEXT NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

CREATE INDEX idx_company_members_company ON company_members(company_id);

-- One row per trained company talent (talent_id is the catalog definition id).
CREATE TABLE company_talents (
    company_id INTEGER NOT NULL,
    talent_id TEXT NOT NULL,
    rank INTEGER NOT NULL,
    PRIMARY KEY (company_id, talent_id),
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

-- Every administrative mutation (membership changes, talent spends, resets) is recorded here.
CREATE TABLE company_audit (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    character_id INTEGER NOT NULL,
    action TEXT NOT NULL,
    detail TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_company_audit_company ON company_audit(company_id);
