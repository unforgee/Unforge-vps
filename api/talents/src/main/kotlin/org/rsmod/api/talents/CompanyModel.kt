package org.rsmod.api.talents

/**
 * One character's company membership, keyed by `character_id` in `company_members`. Lives in
 * [CompanyService]'s in-memory map for the duration of the session - restored by the character-data
 * pipeline at login and removed when the character leaves.
 */
public data class CompanyMembership(
    public val characterId: Int,
    public val companyId: Int,
    public val role: CompanyRole,
)

/**
 * The shared company talent-tree state cached per online company.
 *
 * [points] and [ranks] are the authoritative in-memory copy of `companies.talent_points` +
 * `company_talents`; mutations set [dirty] and the next member's character save flushes them inside
 * that member's db transaction (the `ironman_group_storage` contract). [pendingAudit] accumulates
 * administrative actions that flush alongside.
 *
 * Access to mutable state is guarded by `synchronized(state)` because the db thread reads it during
 * save while the game thread mutates it during play.
 */
public class CompanyState(public val id: Int, public val name: String?) {
    public var points: Int = 0
    public val ranks: MutableMap<String, Int> = mutableMapOf()
    public val pendingAudit: MutableList<CompanyAuditEntry> = mutableListOf()
    public var dirty: Boolean = false

    /** definition id -> current rank snapshot for every company talent. */
    public fun rankSnapshot(): Map<String, Int> =
        TalentCatalog.company.associate { it.id to (ranks[it.id] ?: 0) }
}

/** One deferred `company_audit` row. */
public class CompanyAuditEntry(
    public val characterId: Int,
    public val action: String,
    public val detail: String?,
)
