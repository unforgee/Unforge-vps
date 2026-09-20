package org.rsmod.api.account.character.inv

import java.sql.Connection
import java.sql.DriverManager
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.inv.InvScope
import org.rsmod.game.type.inv.InvStackType
import org.rsmod.game.type.inv.UnpackedInvType

/**
 * Regression for the save crash (`SQLITE_CONSTRAINT_TRIGGER ... equipment instance template
 * mismatch`) that was observed in `server-public.log` around the quest/perk journal tab.
 *
 * `inventory_objs` has a V9 trigger that aborts any write whose `obj` does not match the instance's
 * `template_obj` when `equipment_instance_id != 0`. The save used to persist the live obj id, so a
 * transformed/derived instance made every save fail. The fix persists the canonical template id.
 */
class CharacterInventoryPipelineTest {
    private fun newConnection(): Connection {
        val raw = DriverManager.getConnection("jdbc:sqlite::memory:")
        raw.createStatement().use { it.execute("PRAGMA foreign_keys = ON;") }
        Flyway.configure()
            .dataSource(raw)
            .locations("classpath:db/migration", "classpath:plugin/**/migration")
            .load()
            .migrate()
        return raw
    }

    @Test
    fun `save persists the canonical template id for a transformed instance`() = runBlocking {
        val connection = newConnection()

        connection
            .prepareStatement(
                """
                INSERT INTO characters (account_id, realm_id)
                VALUES (1, 1)
            """
            )
            .use { it.executeUpdate() }
        val characterId = 1
        val instanceId = 77L

        // Template obj is 1; the inventory will carry a transformed obj (999) for that instance.
        connection
            .prepareStatement(
                """
                INSERT INTO equipment_instances
                    (id, template_obj, rarity, roll_seed, schema_version, balance_version, source,
                     bound_character_id)
                VALUES (?, 1, 'test', 0, 1, 1, 'test', ?)
            """
            )
            .use {
                it.setLong(1, instanceId)
                it.setInt(2, characterId)
                it.executeUpdate()
            }
        connection
            .prepareStatement("INSERT INTO inventories (character_id, inv_type) VALUES (?, 0)")
            .use {
                it.setInt(1, characterId)
                it.executeUpdate()
            }

        val player = Player()
        val invType =
            UnpackedInvType(
                scope = InvScope.Perm,
                stack = InvStackType.Never,
                size = 28,
                flags = 0,
                stock = null,
                internalId = 0,
                internalName = "test",
            )
        val inventory = Inventory(invType, arrayOfNulls(28))
        inventory[0] = InvObj(999, 1, instanceId = instanceId)
        player.invMap[invType] = inventory

        val db = DatabaseConnection(connection)
        CharacterInventoryPipeline().save(db, player, characterId)

        val saved =
            db.prepareStatement(
                    "SELECT obj, equipment_instance_id FROM inventory_objs WHERE slot = 0"
                )
                .use {
                    it.executeQuery().use { rs ->
                        if (rs.next()) {
                            rs.getInt("obj") to rs.getLong("equipment_instance_id")
                        } else {
                            null
                        }
                    }
                }
        assertNotNull(saved, "Expected the transformed obj to be saved under its instance.")
        // The persisted obj must be the canonical template (1), not the live transformed id (999).
        assertEquals(1 to instanceId, saved)
    }

    @Test
    fun `inventoriesWithInstances collects non-zero instance ids`() {
        val invType =
            UnpackedInvType(
                scope = InvScope.Perm,
                stack = InvStackType.Never,
                size = 3,
                flags = 0,
                stock = null,
                internalId = 0,
                internalName = "test",
            )
        val inv = Inventory(invType, arrayOfNulls(3))
        inv[0] = InvObj(1, 1) // no instance
        inv[1] = InvObj(2, 1, instanceId = 55L)
        inv[2] = InvObj(3, 1, instanceId = 0L) // zero is "no instance"
        assertEquals(setOf(55L), CharacterInventoryPipeline().inventoriesWithInstances(listOf(inv)))
    }

    @Test
    fun `selectInstanceTemplates resolves template ids`() {
        val connection = newConnection()
        connection
            .prepareStatement(
                """
                INSERT INTO equipment_instances
                    (id, template_obj, rarity, roll_seed, schema_version, balance_version, source)
                VALUES (1, 42, 'test', 0, 1, 1, 'test')
            """
            )
            .use { it.executeUpdate() }
        val db = DatabaseConnection(connection)
        assertEquals(
            mapOf(1L to 42),
            CharacterInventoryPipeline().selectInstanceTemplates(db, setOf(1L)),
        )
    }
}
