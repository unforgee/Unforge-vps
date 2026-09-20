package org.rsmod.api.ironman

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.game.entity.Player

/**
 * Character data stage that loads a group ironman member's shared storage on login and flushes
 * dirty storage/audit rows inside the same transaction as the character save.
 */
@Singleton
public class IronmanDataPipeline
@Inject
constructor(private val groupIronman: GroupIronmanService) : CharacterDataStage.Pipeline {
    override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {
        // Group storage is lazy-loaded on first access / when a member logs in through
        // `loadPlayerGroup`; nothing else is required at the metadata stage.
    }

    override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
        groupIronman.savePlayerGroupData(connection, player, characterId)
    }
}
