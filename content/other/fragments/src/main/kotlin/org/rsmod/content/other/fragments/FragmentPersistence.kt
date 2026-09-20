package org.rsmod.content.other.fragments

import jakarta.inject.Inject
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.game.entity.Player

internal object FragmentCodec {
    fun encode(progress: Map<String, FragmentProgress>): String =
        progress.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value.xp}" }

    fun decode(value: String?): Map<String, FragmentProgress> =
        value
            .orEmpty()
            .split(';')
            .mapNotNull { token ->
                val pair = token.split('=', limit = 2)
                if (pair.size != 2) return@mapNotNull null
                val id =
                    pair[0].takeIf { it in FragmentCatalog.fragmentsById } ?: return@mapNotNull null
                val xp = pair[1].toIntOrNull() ?: return@mapNotNull null
                id to FragmentProgress(FragmentXp.capXp(xp), FragmentXp.levelFor(xp))
            }
            .toMap()
}

internal class FragmentRepository(private val connection: DatabaseConnection) {
    fun load(characterId: Int): FragmentState? {
        val statement =
            connection.prepareStatement(
                "SELECT progress FROM fragment_progression WHERE character_id = ?"
            )
        statement.use {
            it.setInt(1, characterId)
            it.executeQuery().use { rs ->
                if (!rs.next()) return null
                return FragmentState(FragmentCodec.decode(rs.getString("progress")))
            }
        }
    }

    fun save(characterId: Int, state: FragmentState) {
        val statement =
            connection.prepareStatement(
                """
            INSERT INTO fragment_progression (character_id, progress, updated_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(character_id) DO UPDATE SET
              progress = excluded.progress,
              updated_at = CURRENT_TIMESTAMP
            """
                    .trimIndent()
            )
        statement.use {
            it.setInt(1, characterId)
            it.setString(2, FragmentCodec.encode(state.progress))
            it.executeUpdate()
        }
    }
}

public data class FragmentCharacterData(public val state: FragmentState) :
    CharacterDataStage.Segment

public class FragmentCharacterApplier @Inject constructor(private val fragments: FragmentService) :
    CharacterDataStage.Applier<FragmentCharacterData> {
    override fun apply(player: Player, data: FragmentCharacterData) {
        fragments.restore(player.characterId, data.state)
    }
}

public class FragmentCharacterPipeline
@Inject
constructor(private val applier: FragmentCharacterApplier, private val fragments: FragmentService) :
    CharacterDataStage.Pipeline {
    override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {
        val state = FragmentRepository(connection).load(metadata.characterId) ?: FragmentState()
        metadata.add(applier, FragmentCharacterData(state))
    }

    override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
        fragments.state(characterId)?.let { FragmentRepository(connection).save(characterId, it) }
    }
}
