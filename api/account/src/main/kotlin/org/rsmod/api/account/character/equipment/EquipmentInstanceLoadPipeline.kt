package org.rsmod.api.account.character.equipment

import jakarta.inject.Inject
import java.util.UUID
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.equipment.instance.EquipmentInstance
import org.rsmod.api.equipment.instance.EquipmentInstanceFingerprint
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.equipment.instance.EquipmentInstanceRepository
import org.rsmod.api.equipment.instance.EquipmentInstanceSnapshotCodec
import org.rsmod.api.equipment.instance.ItemActor
import org.rsmod.api.equipment.instance.ItemActorType
import org.rsmod.api.equipment.instance.ItemEventHasher
import org.rsmod.api.equipment.instance.ItemMutationEvent
import org.rsmod.api.equipment.instance.ItemMutationOperation
import org.rsmod.game.entity.Player

public class EquipmentInstanceLoadPipeline
@Inject
constructor(
    private val registry: EquipmentInstanceRegistry,
    private val repository: EquipmentInstanceRepository,
) : CharacterDataStage.Pipeline {
    override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {
        connection
            .prepareStatement(
                """
                SELECT DISTINCT io.equipment_instance_id
                FROM inventory_objs io
                JOIN inventories i ON i.id = io.inventories_id
                WHERE i.character_id = ? AND io.equipment_instance_id > 0
            """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, metadata.characterId)
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        val id = resultSet.getLong(1)
                        repository.load(connection, id)?.let { baseline(connection, it) }
                    }
                }
            }

        connection
            .prepareStatement(
                "SELECT gear_instance_ids FROM companions WHERE owner_character_id = ?"
            )
            .use { statement ->
                statement.setLong(1, metadata.characterId.toLong())
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        val gearCsv = resultSet.getString(1) ?: continue
                        for (idStr in gearCsv.split(',')) {
                            val id = idStr.trim().toLongOrNull() ?: continue
                            if (id > 0L) {
                                repository.load(connection, id)?.let { baseline(connection, it) }
                            }
                        }
                    }
                }
            }
    }

    /**
     * Registers [instance] and, on its first Evolution 2.0 load, stamps the canonical fingerprint
     * and writes the `MIGRATED` baseline event inside the same transaction. Rows migrated by V32
     * carry an empty fingerprint until this point - computing it lazily keeps the SQL migration
     * fast and the fingerprint authoritative.
     */
    private fun baseline(connection: DatabaseConnection, instance: EquipmentInstance) {
        val stamped =
            if (instance.fingerprint.isEmpty()) {
                val stamped = instance.copy(fingerprint = EquipmentInstanceFingerprint.of(instance))
                repository.update(connection, stamped)
                stamped
            } else {
                instance
            }
        // The baseline event is written whenever the instance has no audit history yet - this
        // also covers the edge where a previous load stamped the fingerprint but crashed before
        // appending the event.
        if (repository.latestEvent(connection, stamped.instanceId) == null) {
            val unsigned =
                ItemMutationEvent(
                    eventId = UUID.randomUUID(),
                    instanceId = stamped.instanceId,
                    operation = ItemMutationOperation.MIGRATED,
                    actor = ItemActor(ItemActorType.MIGRATION, null),
                    source = "v32-baseline",
                    beforeRevision = stamped.revision,
                    afterRevision = stamped.revision,
                    beforeFingerprint = "",
                    afterFingerprint = stamped.fingerprint,
                    payload = "baseline-fingerprint",
                    previousEventHash = null,
                    eventHash = "",
                    createdAtEpochMillis = System.currentTimeMillis(),
                    // Carrying the baseline snapshot makes the migration point a valid
                    // `restoreFromEvent` target as well.
                    snapshot = EquipmentInstanceSnapshotCodec.encode(stamped),
                )
            repository.appendEvent(
                connection,
                unsigned.copy(eventHash = ItemEventHasher.hash(unsigned)),
            )
        }
        registry.put(stamped)
    }

    override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
        val savedIds = mutableSetOf<Long>()
        for (inv in player.invMap.values) {
            for (obj in inv.objs) {
                if (obj != null && obj.instanceId > 0L && savedIds.add(obj.instanceId)) {
                    val inst = registry[obj.instanceId]
                    if (inst != null) {
                        val updated = repository.upsert(connection, inst)
                        if (updated.instanceId != inst.instanceId) {
                            registry.put(updated)
                        }
                    }
                }
            }
        }

        connection
            .prepareStatement(
                "SELECT gear_instance_ids FROM companions WHERE owner_character_id = ?"
            )
            .use { statement ->
                statement.setLong(1, characterId.toLong())
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        val gearCsv = resultSet.getString(1) ?: continue
                        for (idStr in gearCsv.split(',')) {
                            val id = idStr.trim().toLongOrNull() ?: continue
                            if (id > 0L && savedIds.add(id)) {
                                val inst = registry[id]
                                if (inst != null) {
                                    val updated = repository.upsert(connection, inst)
                                    if (updated.instanceId != inst.instanceId) {
                                        registry.put(updated)
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }
}
