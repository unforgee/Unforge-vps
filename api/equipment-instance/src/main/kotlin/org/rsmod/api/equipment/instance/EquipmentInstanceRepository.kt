package org.rsmod.api.equipment.instance

import jakarta.inject.Inject
import java.sql.Statement
import java.util.UUID
import org.rsmod.api.db.DatabaseConnection

/**
 * Persistence for the canonical equipment-instance snapshot and its append-only event log.
 *
 * All write functions run inside the caller's `Database.withTransaction` block (the db gateway
 * wraps every request in one), so a snapshot update and its [ItemMutationEvent] are committed
 * atomically or not at all.
 *
 * Direct field mutation is deliberately not exposed: every state transition goes through
 * [saveWithRevision] (optimistic locking) or the whole-row [upsert] used by the character save
 * pipeline.
 */
public class EquipmentInstanceRepository @Inject constructor() {
    public fun create(connection: DatabaseConnection, value: EquipmentInstance): EquipmentInstance {
        require(value.instanceId == 0L)
        val uuid = value.instanceUuid.ifEmpty { UUID.randomUUID().toString() }
        val persisted =
            value.copy(
                instanceUuid = uuid,
                fingerprint = value.fingerprint.ifEmpty { EquipmentInstanceFingerprint.of(value) },
            )
        val effects = persisted.uniqueEffectIds.joinToString(",")
        val id =
            connection
                .prepareStatement(
                    """
                    INSERT INTO equipment_instances
                    (template_obj, rarity, roll_seed, schema_version, balance_version, source,
                     category, unique_effect_id, unique_effect_ids, item_tier, item_level, quality,
                     locked_affix_slots, reforge_count, reforge_history, instance_uuid, state,
                     binding, owner_account_id, owner_character_id, mastery_level, experience,
                     evolution_stage, evolution_branch, revision, fingerprint,
                     lineage_parent_ids, lineage_recipe_id, lineage_drop_id, upgrade_level,
                     mystery_enchant_id, mystery_enchant_tier, mystery_enchant_kills_remaining,
                     mystery_enchant_kills_max)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """
                        .trimIndent(),
                    Statement.RETURN_GENERATED_KEYS,
                )
                .use { s ->
                    bindCore(s, persisted, offset = 0, includeId = false)
                    s.executeUpdate()
                    s.generatedKeys.use { k ->
                        check(k.next())
                        k.getLong(1)
                    }
                }
        insertAffixes(connection, id, persisted.affixes)
        insertSockets(connection, id, persisted.sockets)
        insertSkillAffixes(connection, id, persisted.skillAffixes)
        upsertEvolution(connection, id, persisted)
        return persisted.copy(instanceId = id)
    }

    public fun upsert(connection: DatabaseConnection, value: EquipmentInstance): EquipmentInstance {
        if (value.instanceId == 0L) {
            return create(connection, value)
        }
        connection
            .prepareStatement(
                """
                INSERT INTO equipment_instances
                (id, template_obj, rarity, roll_seed, schema_version, balance_version, source,
                 category, unique_effect_id, unique_effect_ids, item_tier, item_level, quality,
                 locked_affix_slots, reforge_count, reforge_history, instance_uuid, state,
                 binding, owner_account_id, owner_character_id, mastery_level, experience,
                 evolution_stage, evolution_branch, revision, fingerprint,
                 lineage_parent_ids, lineage_recipe_id, lineage_drop_id, upgrade_level,
                 mystery_enchant_id, mystery_enchant_tier, mystery_enchant_kills_remaining,
                 mystery_enchant_kills_max)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                rarity = excluded.rarity,
                balance_version = excluded.balance_version,
                source = excluded.source,
                category = excluded.category,
                unique_effect_id = excluded.unique_effect_id,
                unique_effect_ids = excluded.unique_effect_ids,
                item_tier = excluded.item_tier,
                item_level = excluded.item_level,
                quality = excluded.quality,
                locked_affix_slots = excluded.locked_affix_slots,
                reforge_count = excluded.reforge_count,
                reforge_history = excluded.reforge_history,
                instance_uuid = excluded.instance_uuid,
                state = excluded.state,
                binding = excluded.binding,
                owner_account_id = excluded.owner_account_id,
                owner_character_id = excluded.owner_character_id,
                mastery_level = excluded.mastery_level,
                experience = excluded.experience,
                evolution_stage = excluded.evolution_stage,
                evolution_branch = excluded.evolution_branch,
                revision = excluded.revision,
                fingerprint = excluded.fingerprint,
                lineage_parent_ids = excluded.lineage_parent_ids,
                lineage_recipe_id = excluded.lineage_recipe_id,
                lineage_drop_id = excluded.lineage_drop_id,
                upgrade_level = excluded.upgrade_level,
                mystery_enchant_id = excluded.mystery_enchant_id,
                mystery_enchant_tier = excluded.mystery_enchant_tier,
                mystery_enchant_kills_remaining = excluded.mystery_enchant_kills_remaining,
                mystery_enchant_kills_max = excluded.mystery_enchant_kills_max,
                updated_at = CURRENT_TIMESTAMP
                """
                    .trimIndent()
            )
            .use { s ->
                s.setLong(1, value.instanceId)
                bindCore(s, value, offset = 1, includeId = true)
                s.executeUpdate()
            }
        replaceChildren(connection, value)
        upsertEvolution(connection, value.instanceId, value)
        return value
    }

    /**
     * Re-writes every mutable field of an existing instance: affixes and sockets are replaced
     * wholesale (delete + insert), core columns are updated in place. `template_obj`, `roll_seed`
     * and `schema_version` are identity fields and are intentionally not modified.
     *
     * Prefer [saveWithRevision] for gameplay mutations - it additionally enforces the optimistic
     * revision check this raw update skips for the character save pipeline.
     */
    public fun update(connection: DatabaseConnection, value: EquipmentInstance): EquipmentInstance {
        val updated =
            connection
                .prepareStatement(
                    """
                    UPDATE equipment_instances SET
                    rarity = ?, balance_version = ?, source = ?, category = ?,
                    unique_effect_id = ?, unique_effect_ids = ?, item_tier = ?, item_level = ?,
                    quality = ?, locked_affix_slots = ?, reforge_count = ?, reforge_history = ?,
                    instance_uuid = ?, state = ?, binding = ?, owner_account_id = ?,
                    owner_character_id = ?, mastery_level = ?, experience = ?,
                    evolution_stage = ?, evolution_branch = ?, revision = ?, fingerprint = ?,
                    lineage_parent_ids = ?, lineage_recipe_id = ?, lineage_drop_id = ?,
                    upgrade_level = ?, mystery_enchant_id = ?, mystery_enchant_tier = ?,
                    mystery_enchant_kills_remaining = ?, mystery_enchant_kills_max = ?,
                    updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """
                        .trimIndent()
                )
                .use { s ->
                    bindMutable(s, value, offset = 0)
                    s.setLong(32, value.instanceId)
                    s.executeUpdate()
                }
        check(updated == 1) { "No equipment instance with id=${value.instanceId} to update." }
        replaceChildren(connection, value)
        upsertEvolution(connection, value.instanceId, value)
        return value
    }

    /**
     * The single canonical write path for gameplay mutations: the row is only updated when its
     * stored revision still equals [expectedRevision], so two stale writers can never both commit.
     * Returns `false` on a revision conflict (caller resolves the live row and reports
     * [ItemMutationResult.RevisionConflict]).
     */
    public fun saveWithRevision(
        connection: DatabaseConnection,
        value: EquipmentInstance,
        expectedRevision: Long,
    ): Boolean {
        val updated =
            connection
                .prepareStatement(
                    """
                    UPDATE equipment_instances SET
                    rarity = ?, balance_version = ?, source = ?, category = ?,
                    unique_effect_id = ?, unique_effect_ids = ?, item_tier = ?, item_level = ?,
                    quality = ?, locked_affix_slots = ?, reforge_count = ?, reforge_history = ?,
                    instance_uuid = ?, state = ?, binding = ?, owner_account_id = ?,
                    owner_character_id = ?, mastery_level = ?, experience = ?,
                    evolution_stage = ?, evolution_branch = ?, revision = ?, fingerprint = ?,
                    lineage_parent_ids = ?, lineage_recipe_id = ?, lineage_drop_id = ?,
                    upgrade_level = ?, mystery_enchant_id = ?, mystery_enchant_tier = ?,
                    mystery_enchant_kills_remaining = ?, mystery_enchant_kills_max = ?,
                    updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND revision = ?
                    """
                        .trimIndent()
                )
                .use { s ->
                    bindMutable(s, value, offset = 0)
                    s.setLong(32, value.instanceId)
                    s.setLong(33, expectedRevision)
                    s.executeUpdate()
                }
        if (updated != 1) return false
        replaceChildren(connection, value)
        upsertEvolution(connection, value.instanceId, value)
        return true
    }

    private fun replaceChildren(connection: DatabaseConnection, value: EquipmentInstance) {
        for (table in
            listOf(
                "equipment_instance_affixes",
                "equipment_instance_sockets",
                "equipment_instance_skill_affixes",
            )) {
            connection.prepareStatement("DELETE FROM $table WHERE equipment_instance_id = ?").use {
                s ->
                s.setLong(1, value.instanceId)
                s.executeUpdate()
            }
        }
        insertAffixes(connection, value.instanceId, value.affixes)
        insertSockets(connection, value.instanceId, value.sockets)
        insertSkillAffixes(connection, value.instanceId, value.skillAffixes)
    }

    private fun upsertEvolution(
        connection: DatabaseConnection,
        instanceId: Long,
        value: EquipmentInstance,
    ) {
        connection
            .prepareStatement(
                """
                INSERT INTO equipment_instance_evolution
                (equipment_instance_id, stage, branch, unlocked_milestones)
                VALUES (?, ?, ?, '[]')
                ON CONFLICT(equipment_instance_id) DO UPDATE SET
                stage = excluded.stage,
                branch = excluded.branch
                """
                    .trimIndent()
            )
            .use { s ->
                s.setLong(1, instanceId)
                s.setInt(2, value.evolutionStage)
                if (value.evolutionBranch == null) s.setNull(3, java.sql.Types.VARCHAR)
                else s.setString(3, value.evolutionBranch)
                s.executeUpdate()
            }
    }

    // --- column binders (single source of truth for field order) ---

    /** Binds the 34-value core column list used by INSERT statements. */
    private fun bindCore(
        s: java.sql.PreparedStatement,
        v: EquipmentInstance,
        offset: Int,
        includeId: Boolean,
    ) {
        var i = offset
        s.setInt(++i, v.templateObj)
        s.setString(++i, v.rarity.name)
        s.setLong(++i, v.rollSeed)
        s.setInt(++i, v.schemaVersion)
        s.setInt(++i, v.balanceVersion)
        s.setString(++i, v.source)
        s.setString(++i, v.category.name)
        s.setString(++i, v.uniqueEffectIds.firstOrNull())
        s.setString(++i, v.uniqueEffectIds.joinToString(","))
        s.setInt(++i, v.tier.value)
        s.setInt(++i, v.itemLevel)
        s.setInt(++i, v.quality)
        s.setString(++i, v.lockedAffixSlots.sorted().joinToString(","))
        s.setInt(++i, v.reforgeCount)
        s.setString(++i, v.reforgeHistory.joinToString("\n"))
        s.setString(++i, v.instanceUuid)
        s.setString(++i, v.state.name)
        s.setString(++i, v.binding.name)
        if (v.ownerAccountId == null) s.setNull(++i, java.sql.Types.INTEGER)
        else s.setLong(++i, v.ownerAccountId)
        if (v.ownerCharacterId == null) s.setNull(++i, java.sql.Types.INTEGER)
        else s.setLong(++i, v.ownerCharacterId)
        s.setInt(++i, v.masteryLevel)
        s.setLong(++i, v.experience)
        s.setInt(++i, v.evolutionStage)
        if (v.evolutionBranch == null) s.setNull(++i, java.sql.Types.VARCHAR)
        else s.setString(++i, v.evolutionBranch)
        s.setLong(++i, v.revision)
        s.setString(++i, v.fingerprint)
        s.setString(++i, v.lineageParentIds.joinToString(","))
        if (v.lineageRecipeId == null) s.setNull(++i, java.sql.Types.VARCHAR)
        else s.setString(++i, v.lineageRecipeId)
        if (v.lineageDropId == null) s.setNull(++i, java.sql.Types.VARCHAR)
        else s.setString(++i, v.lineageDropId)
        s.setInt(++i, v.upgradeLevel)
        if (v.mysteryEnchantId == null) s.setNull(++i, java.sql.Types.VARCHAR)
        else s.setString(++i, v.mysteryEnchantId.name)
        if (v.mysteryEnchantTier == null) s.setNull(++i, java.sql.Types.VARCHAR)
        else s.setString(++i, v.mysteryEnchantTier.name)
        s.setInt(++i, v.mysteryEnchantKillsRemaining)
        s.setInt(++i, v.mysteryEnchantKillsMax)
        check(includeId || i == offset + 34)
    }

    /** Binds the 31 mutable columns shared by update and saveWithRevision. */
    private fun bindMutable(s: java.sql.PreparedStatement, v: EquipmentInstance, offset: Int) {
        var i = offset
        s.setString(++i, v.rarity.name)
        s.setInt(++i, v.balanceVersion)
        s.setString(++i, v.source)
        s.setString(++i, v.category.name)
        s.setString(++i, v.uniqueEffectIds.firstOrNull())
        s.setString(++i, v.uniqueEffectIds.joinToString(","))
        s.setInt(++i, v.tier.value)
        s.setInt(++i, v.itemLevel)
        s.setInt(++i, v.quality)
        s.setString(++i, v.lockedAffixSlots.sorted().joinToString(","))
        s.setInt(++i, v.reforgeCount)
        s.setString(++i, v.reforgeHistory.joinToString("\n"))
        s.setString(++i, v.instanceUuid)
        s.setString(++i, v.state.name)
        s.setString(++i, v.binding.name)
        if (v.ownerAccountId == null) s.setNull(++i, java.sql.Types.INTEGER)
        else s.setLong(++i, v.ownerAccountId)
        if (v.ownerCharacterId == null) s.setNull(++i, java.sql.Types.INTEGER)
        else s.setLong(++i, v.ownerCharacterId)
        s.setInt(++i, v.masteryLevel)
        s.setLong(++i, v.experience)
        s.setInt(++i, v.evolutionStage)
        if (v.evolutionBranch == null) s.setNull(++i, java.sql.Types.VARCHAR)
        else s.setString(++i, v.evolutionBranch)
        s.setLong(++i, v.revision)
        s.setString(++i, v.fingerprint)
        s.setString(++i, v.lineageParentIds.joinToString(","))
        if (v.lineageRecipeId == null) s.setNull(++i, java.sql.Types.VARCHAR)
        else s.setString(++i, v.lineageRecipeId)
        if (v.lineageDropId == null) s.setNull(++i, java.sql.Types.VARCHAR)
        else s.setString(++i, v.lineageDropId)
        s.setInt(++i, v.upgradeLevel)
        if (v.mysteryEnchantId == null) s.setNull(++i, java.sql.Types.VARCHAR)
        else s.setString(++i, v.mysteryEnchantId.name)
        if (v.mysteryEnchantTier == null) s.setNull(++i, java.sql.Types.VARCHAR)
        else s.setString(++i, v.mysteryEnchantTier.name)
        s.setInt(++i, v.mysteryEnchantKillsRemaining)
        s.setInt(++i, v.mysteryEnchantKillsMax)
        check(i == offset + 31)
    }

    private fun insertAffixes(
        connection: DatabaseConnection,
        instanceId: Long,
        affixes: List<EquipmentAffixRoll>,
    ) {
        connection
            .prepareStatement(
                "INSERT INTO equipment_instance_affixes (equipment_instance_id, slot, affix_id, magnitude) VALUES (?, ?, ?, ?)"
            )
            .use { s ->
                affixes.forEach { a ->
                    s.setLong(1, instanceId)
                    s.setInt(2, a.slot)
                    s.setString(3, a.definitionId)
                    s.setInt(4, a.magnitude)
                    s.addBatch()
                }
                s.executeBatch()
            }
    }

    private fun insertSockets(
        connection: DatabaseConnection,
        instanceId: Long,
        sockets: List<EquipmentSocket>,
    ) {
        connection
            .prepareStatement(
                "INSERT INTO equipment_instance_sockets (equipment_instance_id, slot, socket_type, socketed_obj, magnitude) VALUES (?, ?, ?, ?, ?)"
            )
            .use { s ->
                sockets.forEach { x ->
                    s.setLong(1, instanceId)
                    s.setInt(2, x.slot)
                    s.setString(3, x.type)
                    if (x.socketedObj == null) s.setNull(4, java.sql.Types.INTEGER)
                    else s.setInt(4, x.socketedObj)
                    s.setInt(5, x.magnitude)
                    s.addBatch()
                }
                s.executeBatch()
            }
    }

    private fun insertSkillAffixes(
        connection: DatabaseConnection,
        instanceId: Long,
        affixes: List<SkillAffixRoll>,
    ) {
        connection
            .prepareStatement(
                "INSERT INTO equipment_instance_skill_affixes (equipment_instance_id, slot, skill, effect, unit, magnitude) VALUES (?, ?, ?, ?, ?, ?)"
            )
            .use { s ->
                affixes.forEach { a ->
                    s.setLong(1, instanceId)
                    s.setInt(2, a.slot)
                    s.setString(3, a.skill)
                    s.setString(4, a.effect)
                    s.setString(5, a.unit.name)
                    s.setInt(6, a.magnitude)
                    s.addBatch()
                }
                s.executeBatch()
            }
    }

    // --- queries ---

    public fun load(connection: DatabaseConnection, id: Long): EquipmentInstance? {
        val core =
            connection
                .prepareStatement(
                    """
                    SELECT template_obj, rarity, roll_seed, schema_version, balance_version,
                    source, category, unique_effect_id, unique_effect_ids, item_tier, item_level,
                    quality, locked_affix_slots, reforge_count, reforge_history, instance_uuid,
                    state, binding, owner_account_id, owner_character_id, mastery_level,
                    experience, evolution_stage, evolution_branch, revision, fingerprint,
                    lineage_parent_ids, lineage_recipe_id, lineage_drop_id, created_at,
                    upgrade_level, mystery_enchant_id, mystery_enchant_tier,
                    mystery_enchant_kills_remaining, mystery_enchant_kills_max
                    FROM equipment_instances WHERE id = ?
                    """
                        .trimIndent()
                )
                .use { s ->
                    s.setLong(1, id)
                    s.executeQuery().use { rs ->
                        if (!rs.next()) return null
                        Core(rs)
                    }
                }
        return assemble(connection, id, core)
    }

    public fun loadByUuid(connection: DatabaseConnection, uuid: String): EquipmentInstance? {
        val id =
            connection
                .prepareStatement("SELECT id FROM equipment_instances WHERE instance_uuid = ?")
                .use { s ->
                    s.setString(1, uuid)
                    s.executeQuery().use { rs -> if (!rs.next()) return null else rs.getLong(1) }
                }
        return load(connection, id)
    }

    public fun loadOwnedByCharacter(
        connection: DatabaseConnection,
        characterId: Long,
    ): List<EquipmentInstance> {
        val ids =
            connection
                .prepareStatement(
                    "SELECT id FROM equipment_instances WHERE owner_character_id = ? ORDER BY id"
                )
                .use { s ->
                    s.setLong(1, characterId)
                    s.executeQuery().use { rs ->
                        buildList { while (rs.next()) add(rs.getLong(1)) }
                    }
                }
        return ids.mapNotNull { load(connection, it) }
    }

    /** `true` when the stored fingerprint equals the recomputed canonical fingerprint. */
    public fun validateFingerprint(connection: DatabaseConnection, id: Long): Boolean {
        val instance = load(connection, id) ?: return false
        return EquipmentInstanceFingerprint.verify(instance)
    }

    private fun assemble(connection: DatabaseConnection, id: Long, core: Core): EquipmentInstance {
        val affixes =
            connection
                .prepareStatement(
                    "SELECT slot, affix_id, magnitude FROM equipment_instance_affixes WHERE equipment_instance_id = ? ORDER BY slot"
                )
                .use { s ->
                    s.setLong(1, id)
                    s.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                val d = EquipmentAffixCatalog.byId.getValue(rs.getString(2))
                                add(
                                    EquipmentAffixRoll(
                                        rs.getInt(1),
                                        d.id,
                                        d.family,
                                        d.stat,
                                        d.unit,
                                        d.polarity,
                                        rs.getInt(3),
                                    )
                                )
                            }
                        }
                    }
                }
        val sockets =
            connection
                .prepareStatement(
                    "SELECT slot, socket_type, socketed_obj, magnitude FROM equipment_instance_sockets WHERE equipment_instance_id = ? ORDER BY slot"
                )
                .use { s ->
                    s.setLong(1, id)
                    s.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                val obj = rs.getInt(3).takeUnless { rs.wasNull() }
                                add(
                                    EquipmentSocket(
                                        rs.getInt(1),
                                        rs.getString(2),
                                        obj,
                                        rs.getInt(4),
                                    )
                                )
                            }
                        }
                    }
                }
        val skillAffixes =
            connection
                .prepareStatement(
                    "SELECT slot, skill, effect, unit, magnitude FROM equipment_instance_skill_affixes WHERE equipment_instance_id = ? ORDER BY slot"
                )
                .use { s ->
                    s.setLong(1, id)
                    s.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(
                                    SkillAffixRoll(
                                        rs.getInt(1),
                                        rs.getString(2),
                                        rs.getString(3),
                                        ModifierUnit.valueOf(rs.getString(4)),
                                        rs.getInt(5),
                                    )
                                )
                            }
                        }
                    }
                }
        val effects =
            if (core.effectIds.isBlank()) core.legacyEffect?.let(::listOf).orEmpty()
            else core.effectIds.split(',').filter(String::isNotBlank)
        return EquipmentInstance(
            instanceId = id,
            templateObj = core.templateObj,
            category = core.category,
            rarity = core.rarity,
            rollSeed = core.seed,
            affixes = affixes,
            sockets = sockets,
            uniqueEffectIds = effects,
            source = core.source,
            tier =
                EquipmentTier.entries.firstOrNull { it.value == core.tier } ?: EquipmentTier.Bronze,
            schemaVersion = core.schemaVersion,
            balanceVersion = core.balanceVersion,
            itemLevel = core.itemLevel,
            quality = core.quality,
            lockedAffixSlots =
                core.locked.split(',').filter(String::isNotBlank).map(String::toInt).toSet(),
            reforgeCount = core.reforgeCount,
            reforgeHistory = core.history.split('\n').filter(String::isNotBlank),
            skillAffixes = skillAffixes,
            instanceUuid = core.uuid,
            state =
                runCatching { EquipmentInstanceState.valueOf(core.state) }
                    .getOrDefault(EquipmentInstanceState.ACTIVE),
            binding =
                runCatching { ItemBinding.valueOf(core.binding) }.getOrDefault(ItemBinding.UNBOUND),
            ownerAccountId = core.ownerAccountId,
            ownerCharacterId = core.ownerCharacterId,
            masteryLevel = core.masteryLevel,
            experience = core.experience,
            evolutionStage = core.evolutionStage,
            evolutionBranch = core.evolutionBranch,
            revision = core.revision,
            fingerprint = core.fingerprint,
            lineageParentIds =
                core.lineageParents.split(',').filter(String::isNotBlank).map(String::toLong),
            lineageRecipeId = core.lineageRecipeId,
            lineageDropId = core.lineageDropId,
            createdAtEpochMillis = core.createdAt,
            upgradeLevel = core.upgradeLevel,
            mysteryEnchantId = core.mysteryEnchantId,
            mysteryEnchantTier = core.mysteryEnchantTier,
            mysteryEnchantKillsRemaining = core.mysteryEnchantKillsRemaining,
            mysteryEnchantKillsMax = core.mysteryEnchantKillsMax,
        )
    }

    private data class Core(
        val templateObj: Int,
        val rarity: EquipmentRarity,
        val seed: Long,
        val schemaVersion: Int,
        val balanceVersion: Int,
        val source: String,
        val category: EquipmentCategory,
        val legacyEffect: String?,
        val effectIds: String,
        val tier: Int,
        val itemLevel: Int,
        val quality: Int,
        val locked: String,
        val reforgeCount: Int,
        val history: String,
        val uuid: String,
        val state: String,
        val binding: String,
        val ownerAccountId: Long?,
        val ownerCharacterId: Long?,
        val masteryLevel: Int,
        val experience: Long,
        val evolutionStage: Int,
        val evolutionBranch: String?,
        val revision: Long,
        val fingerprint: String,
        val lineageParents: String,
        val lineageRecipeId: String?,
        val lineageDropId: String?,
        val createdAt: Long,
        val upgradeLevel: Int,
        val mysteryEnchantId: MysteryEnchantId?,
        val mysteryEnchantTier: MysteryEnchantTier?,
        val mysteryEnchantKillsRemaining: Int,
        val mysteryEnchantKillsMax: Int,
    ) {
        constructor(
            rs: java.sql.ResultSet
        ) : this(
            templateObj = rs.getInt(1),
            rarity = EquipmentRarity.valueOf(rs.getString(2)),
            seed = rs.getLong(3),
            schemaVersion = rs.getInt(4),
            balanceVersion = rs.getInt(5),
            source = rs.getString(6),
            category = EquipmentCategory.valueOf(rs.getString(7)),
            legacyEffect = rs.getString(8),
            effectIds = rs.getString(9),
            tier = rs.getInt(10),
            itemLevel = rs.getInt(11),
            quality = rs.getInt(12),
            locked = rs.getString(13),
            reforgeCount = rs.getInt(14),
            history = rs.getString(15),
            uuid = rs.getString(16) ?: "",
            state = rs.getString(17) ?: "ACTIVE",
            binding = rs.getString(18) ?: "UNBOUND",
            ownerAccountId = rs.getLong(19).takeUnless { rs.wasNull() },
            ownerCharacterId = rs.getLong(20).takeUnless { rs.wasNull() },
            masteryLevel = rs.getInt(21),
            experience = rs.getLong(22),
            evolutionStage = rs.getInt(23),
            evolutionBranch = rs.getString(24),
            revision = rs.getLong(25),
            fingerprint = rs.getString(26) ?: "",
            lineageParents = rs.getString(27) ?: "",
            lineageRecipeId = rs.getString(28),
            lineageDropId = rs.getString(29),
            createdAt = rs.getTimestamp(30)?.time ?: 0L,
            upgradeLevel = rs.getInt(31),
            mysteryEnchantId = parseMysteryEnchantId(rs.getString(32)),
            mysteryEnchantTier = parseMysteryEnchantTier(rs.getString(33)),
            mysteryEnchantKillsRemaining = rs.getInt(34),
            mysteryEnchantKillsMax = rs.getInt(35),
        )
    }

    // --- append-only event log ---

    /** Appends [event] to the audit log inside the caller's transaction. */
    public fun appendEvent(connection: DatabaseConnection, event: ItemMutationEvent) {
        appendEvent(connection, event, null)
    }

    /**
     * Appends [event] to the audit log, additionally storing [idempotencyKey] for replay detection
     * when non-null.
     */
    public fun appendEvent(
        connection: DatabaseConnection,
        event: ItemMutationEvent,
        idempotencyKey: String?,
    ) {
        connection
            .prepareStatement(
                """
                INSERT INTO equipment_instance_events
                (event_id, equipment_instance_id, operation, actor_type, actor_id, source,
                 before_revision, after_revision, before_fingerprint, after_fingerprint,
                 payload, previous_event_hash, event_hash, idempotency_key, snapshot)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
                    .trimIndent()
            )
            .use { s ->
                s.setString(1, event.eventId.toString())
                s.setLong(2, event.instanceId)
                s.setString(3, event.operation.name)
                s.setString(4, event.actor.type.name)
                if (event.actor.id == null) s.setNull(5, java.sql.Types.INTEGER)
                else s.setLong(5, event.actor.id)
                s.setString(6, event.source)
                s.setLong(7, event.beforeRevision)
                s.setLong(8, event.afterRevision)
                s.setString(9, event.beforeFingerprint)
                s.setString(10, event.afterFingerprint)
                s.setString(11, event.payload)
                if (event.previousEventHash == null) s.setNull(12, java.sql.Types.VARCHAR)
                else s.setString(12, event.previousEventHash)
                s.setString(13, event.eventHash)
                if (idempotencyKey == null) s.setNull(14, java.sql.Types.VARCHAR)
                else s.setString(14, idempotencyKey)
                if (event.snapshot == null) s.setNull(15, java.sql.Types.VARCHAR)
                else s.setString(15, event.snapshot)
                s.executeUpdate()
            }
    }

    /** The newest event for [instanceId], used to continue the hash chain. */
    public fun latestEvent(connection: DatabaseConnection, instanceId: Long): ItemMutationEvent? =
        connection
            .prepareStatement(
                """
                SELECT event_id, equipment_instance_id, operation, actor_type, actor_id, source,
                before_revision, after_revision, before_fingerprint, after_fingerprint, payload,
                previous_event_hash, event_hash, snapshot, created_at
                FROM equipment_instance_events
                WHERE equipment_instance_id = ?
                ORDER BY after_revision DESC, created_at DESC LIMIT 1
                """
                    .trimIndent()
            )
            .use { s ->
                s.setLong(1, instanceId)
                s.executeQuery().use { rs -> if (!rs.next()) null else rs.toEvent() }
            }

    /** The event previously recorded for [idempotencyKey] on [instanceId], for replay. */
    public fun findEventByIdempotencyKey(
        connection: DatabaseConnection,
        instanceId: Long,
        idempotencyKey: String,
    ): ItemMutationEvent? =
        connection
            .prepareStatement(
                """
                SELECT event_id, equipment_instance_id, operation, actor_type, actor_id, source,
                before_revision, after_revision, before_fingerprint, after_fingerprint, payload,
                previous_event_hash, event_hash, snapshot, created_at
                FROM equipment_instance_events
                WHERE equipment_instance_id = ? AND idempotency_key = ?
                """
                    .trimIndent()
            )
            .use { s ->
                s.setLong(1, instanceId)
                s.setString(2, idempotencyKey)
                s.executeQuery().use { rs -> if (!rs.next()) null else rs.toEvent() }
            }

    /**
     * Event history for [instanceId], oldest first. [cursor] is an exclusive `after_revision` lower
     * bound for pagination; [limit] caps the page size.
     */
    public fun loadHistory(
        connection: DatabaseConnection,
        instanceId: Long,
        cursor: Long = 0L,
        limit: Int = 100,
    ): List<ItemMutationEvent> =
        connection
            .prepareStatement(
                """
                SELECT event_id, equipment_instance_id, operation, actor_type, actor_id, source,
                before_revision, after_revision, before_fingerprint, after_fingerprint, payload,
                previous_event_hash, event_hash, snapshot, created_at
                FROM equipment_instance_events
                WHERE equipment_instance_id = ? AND after_revision > ?
                ORDER BY after_revision ASC, created_at ASC LIMIT ?
                """
                    .trimIndent()
            )
            .use { s ->
                s.setLong(1, instanceId)
                s.setLong(2, cursor)
                s.setInt(3, limit)
                s.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toEvent()) } }
            }

    private fun java.sql.ResultSet.toEvent(): ItemMutationEvent {
        val eventId = UUID.fromString(getString(1))
        val actorId = getLong(5).takeUnless { wasNull() }
        return ItemMutationEvent(
            eventId = eventId,
            instanceId = getLong(2),
            operation = ItemMutationOperation.valueOf(getString(3)),
            actor = ItemActor(ItemActorType.valueOf(getString(4)), actorId),
            source = getString(6),
            beforeRevision = getLong(7),
            afterRevision = getLong(8),
            beforeFingerprint = getString(9),
            afterFingerprint = getString(10),
            payload = getString(11),
            previousEventHash = getString(12),
            eventHash = getString(13),
            snapshot = getString(14),
            createdAtEpochMillis = getTimestamp(15)?.time ?: 0L,
        )
    }
}

private fun parseMysteryEnchantId(raw: String?): MysteryEnchantId? =
    raw?.let { runCatching { MysteryEnchantId.valueOf(it) }.getOrNull() }

private fun parseMysteryEnchantTier(raw: String?): MysteryEnchantTier? =
    raw?.let { runCatching { MysteryEnchantTier.valueOf(it) }.getOrNull() }
