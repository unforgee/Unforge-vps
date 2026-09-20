package org.rsmod.api.companion

import jakarta.inject.Inject
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.game.entity.Player

public data class CompanionFrameLayoutData(public val layout: CompanionFrameLayout) :
    CharacterDataStage.Segment

public class CompanionFrameLayoutApplier
@Inject
constructor(private val layouts: CompanionFrameLayoutStore) :
    CharacterDataStage.Applier<CompanionFrameLayoutData> {
    override fun apply(player: Player, data: CompanionFrameLayoutData) {
        layouts.restore(player.characterId.toLong(), data.layout)
    }
}

public class CompanionFrameLayoutPipeline
@Inject
constructor(
    private val applier: CompanionFrameLayoutApplier,
    private val layouts: CompanionFrameLayoutStore,
) : CharacterDataStage.Pipeline {
    override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {
        val layout =
            connection
                .prepareStatement(
                    "SELECT x, y, width, height, scale_percent FROM companion_ui_layout WHERE character_id = ?"
                )
                .use { statement ->
                    statement.setInt(1, metadata.characterId)
                    statement.executeQuery().use { rows ->
                        if (rows.next())
                            CompanionFrameLayout(
                                rows.getInt(1),
                                rows.getInt(2),
                                rows.getInt(3),
                                rows.getInt(4),
                                rows.getInt(5),
                            )
                        else layouts.get(metadata.characterId.toLong())
                    }
                }
        metadata.add(applier, CompanionFrameLayoutData(layout))
    }

    override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
        val layout = layouts.get(characterId.toLong())
        connection
            .prepareStatement(
                "INSERT INTO companion_ui_layout (character_id, x, y, width, height, scale_percent) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(character_id) DO UPDATE SET x=excluded.x, y=excluded.y, width=excluded.width, height=excluded.height, scale_percent=excluded.scale_percent"
            )
            .use { statement ->
                statement.setInt(1, characterId)
                statement.setInt(2, layout.x)
                statement.setInt(3, layout.y)
                statement.setInt(4, layout.width)
                statement.setInt(5, layout.height)
                statement.setInt(6, layout.scalePercent)
                statement.executeUpdate()
            }
    }
}
