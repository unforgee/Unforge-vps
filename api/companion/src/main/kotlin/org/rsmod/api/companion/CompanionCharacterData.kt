package org.rsmod.api.companion

import jakarta.inject.Inject
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.game.entity.Player

public data class CompanionCharacterData(
    public val companions: List<Companion>,
    /** companion id -> (skill -> experience) for the SUPPORT/TANK progression tracks. */
    public val skillExperience: Map<Long, Map<CompanionSkill, Long>> = emptyMap(),
) : CharacterDataStage.Segment

public class CompanionCharacterApplier
@Inject
constructor(
    private val service: CompanionService,
    private val skillService: CompanionSkillService,
) : CharacterDataStage.Applier<CompanionCharacterData> {
    override fun apply(player: Player, data: CompanionCharacterData) {
        service.restore(player.characterId.toLong(), data.companions)
        skillService.restore(player.characterId.toLong(), data.skillExperience)
    }
}

public class CompanionCharacterPipeline
@Inject
constructor(
    private val applier: CompanionCharacterApplier,
    private val service: CompanionService,
    private val skillService: CompanionSkillService,
) : CharacterDataStage.Pipeline {
    override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {
        metadata.add(
            applier,
            CompanionCharacterData(
                CompanionRepository(connection).load(metadata.characterId.toLong()),
                CompanionSkillRepository(connection).load(metadata.characterId.toLong()),
            ),
        )
    }

    override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
        val repository = CompanionRepository(connection)
        val skillRepository = CompanionSkillRepository(connection)
        val ownerId = characterId.toLong()
        // Companion rows must exist before their skill rows are written (foreign key).
        service.owned(ownerId).forEach(repository::save)
        service.owned(ownerId).forEach { companion ->
            skillService.experienceMap(companion.id).forEach { (skill, xp) ->
                skillRepository.save(companion.id, ownerId, skill, xp)
            }
        }
    }
}
