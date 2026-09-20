package org.rsmod.api.account.character.inv

import jakarta.inject.Inject
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.inv.InvScope

private typealias CharacterInventory = CharacterInventoryData.Inventory

private typealias CharacterObj = CharacterInventoryData.Obj

public class CharacterInventoryPipeline
@Inject
constructor(private val applier: CharacterInventoryApplier) : CharacterDataStage.Pipeline {
    override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {
        val inventories = selectInventories(connection, metadata)

        // Avoid a malformed query if no inventories exist.
        if (inventories.isEmpty()) {
            metadata.add(applier, CharacterInventoryData(inventories))
            return
        }

        val rowInventories = inventories.associateBy { it.rowId }

        val placeholders = (0 until inventories.size).joinToString(",") { "?" }
        val select =
            connection.prepareStatement(
                """
                    SELECT inventories_id, slot, obj, count, vars, equipment_instance_id
                    FROM inventory_objs
                    WHERE inventories_id IN ($placeholders)
                """
                    .trimIndent()
            )

        select.use {
            inventories.forEachIndexed { index, inventory -> it.setInt(1 + index, inventory.rowId) }
            it.executeQuery().use { resultSet ->
                while (resultSet.next()) {
                    val inventoriesRow = resultSet.getInt("inventories_id")
                    val slot = resultSet.getInt("slot")
                    val obj = resultSet.getInt("obj")
                    val count = resultSet.getInt("count")
                    val vars = resultSet.getInt("vars")
                    val instanceId = resultSet.getLong("equipment_instance_id")

                    val inventory = rowInventories.getValue(inventoriesRow)
                    inventory.objs[slot] = CharacterObj(obj, count, vars, instanceId)
                }
            }
        }

        metadata.add(applier, CharacterInventoryData(inventories))
    }

    private fun selectInventories(
        connection: DatabaseConnection,
        metadata: CharacterMetadataList,
    ): List<CharacterInventory> {
        val inventories = ArrayList<CharacterInventory>(4)

        val select =
            connection.prepareStatement(
                """
                    SELECT id, inv_type
                    FROM inventories
                    WHERE character_id = ?
                """
                    .trimIndent()
            )

        select.use {
            it.setInt(1, metadata.characterId)
            it.executeQuery().use { resultSet ->
                while (resultSet.next()) {
                    val id = resultSet.getInt("id")
                    val type = resultSet.getInt("inv_type")
                    inventories += CharacterInventory(rowId = id, type = type)
                }
            }
        }

        return inventories
    }

    override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
        val persistentInvs = player.invMap.values.filter { it.type.scope == InvScope.Perm }
        deleteStaleInventories(connection, characterId, persistentInvs)

        val delete =
            connection.prepareStatement(
                """
                    DELETE FROM inventory_objs
                    WHERE inventories_id = ? AND slot = ?
                """
                    .trimIndent()
            )

        // Note: Not all database engines support `ON CONFLICT`. This syntax works with our current
        // database setup (sqlite), but may need to be adapted for others (e.g., mysql uses
        // `ON DUPLICATE KEY UPDATE` for similar functionality).
        val upsert =
            connection.prepareStatement(
                """
                    INSERT INTO inventory_objs
                        (inventories_id, slot, obj, count, vars, equipment_instance_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(inventories_id, slot) DO UPDATE SET
                        obj = excluded.obj,
                        count = excluded.count,
                        vars = excluded.vars,
                        equipment_instance_id = excluded.equipment_instance_id
                """
                    .trimIndent()
            )

        // `inventory_objs` carries a `BEFORE UPDATE/DELETE .. WHEN equipment_instance_id != 0`
        // trigger (V9) that aborts any save whose obj id does not match the instance's
        // `template_obj`. Inventory can legitimately hold a transformed/derived variant of the
        // template (e.g. charged, cosmetic or placeholder forms share the instance), so we resolve
        // the canonical template id here and persist that instead of the live `id` - the trigger
        // then stays satisfied and the save never dies on a mismatched row.
        val templateByInstance =
            selectInstanceTemplates(connection, inventoriesWithInstances(persistentInvs))
        delete.use { delete ->
            upsert.use { upsert ->
                for (inventory in persistentInvs) {
                    val type = inventory.type

                    val inventoryRowId = getOrInsertInventoryRowId(connection, characterId, type.id)
                    if (inventoryRowId == null) {
                        val message =
                            "Fatal error fetching inventory row for: $type (player=$player)"
                        throw IllegalStateException(message)
                    }

                    for (i in inventory.indices) {
                        if (inventory[i] == null) {
                            delete.setInt(1, inventoryRowId)
                            delete.setInt(2, i)
                            delete.addBatch()
                        }
                    }

                    for (i in inventory.indices) {
                        val obj = inventory[i]
                        if (obj != null) {
                            val persistedObjId = templateByInstance[obj.instanceId] ?: obj.id
                            upsert.setInt(1, inventoryRowId)
                            upsert.setInt(2, i)
                            upsert.setInt(3, persistedObjId)
                            upsert.setInt(4, obj.count)
                            upsert.setInt(5, obj.vars)
                            upsert.setLong(6, obj.instanceId)
                            upsert.addBatch()
                        }
                    }
                }
                delete.executeBatch()
                upsert.executeBatch()
            }
        }
    }

    /** Every `equipment_instance_id` (non-zero) appearing in the inventory being saved. */
    internal fun inventoriesWithInstances(inventories: Collection<Inventory>): Set<Long> =
        inventories
            .asSequence()
            .flatMap { inv -> inv.objs.asSequence().mapNotNull { it?.instanceId } }
            .filter { it != 0L }
            .toSet()

    /**
     * Loads `template_obj` for the requested instance ids so the save can persist the canonical
     * template id (matching the V9 trigger) instead of a live transformed variant.
     */
    internal fun selectInstanceTemplates(
        connection: DatabaseConnection,
        instanceIds: Set<Long>,
    ): Map<Long, Int> {
        if (instanceIds.isEmpty()) {
            return emptyMap()
        }
        val placeholders = instanceIds.joinToString(",") { "?" }
        val select =
            connection.prepareStatement(
                """
                    SELECT id, template_obj
                    FROM equipment_instances
                    WHERE id IN ($placeholders)
                """
                    .trimIndent()
            )
        val result = HashMap<Long, Int>(instanceIds.size)
        select.use {
            instanceIds.forEachIndexed { index, id -> it.setLong(1 + index, id) }
            it.executeQuery().use { rs ->
                while (rs.next()) {
                    result[rs.getLong("id")] = rs.getInt("template_obj")
                }
            }
        }
        return result
    }

    private fun deleteStaleInventories(
        connection: DatabaseConnection,
        characterId: Int,
        inventories: Collection<Inventory>,
    ) {
        // Important: This function assumes `inventory_objs` references `inventories` with
        // `ON DELETE CASCADE`, so deleting a parent inventory also deletes its associated
        // `inventory_objs` rows.
        if (inventories.isNotEmpty()) {
            val activeInvPlaceholders = (0 until inventories.size).joinToString(",") { "?" }
            val deleteStaleInventories =
                connection.prepareStatement(
                    """
                        DELETE FROM inventories
                        WHERE character_id = ? AND inv_type NOT IN ($activeInvPlaceholders)
                    """
                        .trimIndent()
                )

            deleteStaleInventories.use {
                it.setInt(1, characterId)
                inventories.forEachIndexed { index, inv -> it.setInt(2 + index, inv.type.id) }
                it.executeUpdate()
            }
        } else {
            val deleteAllInventories =
                connection.prepareStatement("DELETE FROM inventories WHERE character_id = ?")

            deleteAllInventories.use {
                it.setInt(1, characterId)
                it.executeUpdate()
            }
        }
    }

    private fun getOrInsertInventoryRowId(
        connection: DatabaseConnection,
        characterId: Int,
        invType: Int,
    ): Int? {
        // Note: Not all database engines support `ON CONFLICT`. This syntax works with our current
        // database setup (sqlite), but may need to be adapted for others (e.g., mysql uses
        // `INSERT IGNORE`).
        val insert =
            connection.prepareStatement(
                """
                    INSERT INTO inventories (character_id, inv_type)
                    VALUES (?, ?)
                    ON CONFLICT(character_id, inv_type) DO NOTHING
                """
                    .trimIndent()
            )

        insert.use {
            it.setInt(1, characterId)
            it.setInt(2, invType)
            it.executeUpdate()
        }

        val select =
            connection.prepareStatement(
                """
                    SELECT id FROM inventories
                    WHERE character_id = ? AND inv_type = ?
                """
                    .trimIndent()
            )

        select.use {
            it.setInt(1, characterId)
            it.setInt(2, invType)
            val rowId =
                select.executeQuery().use { rs ->
                    if (rs.next()) {
                        rs.getInt("id")
                    } else {
                        null
                    }
                }
            return rowId
        }
    }
}
