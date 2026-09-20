package org.rsmod.api.talents

import jakarta.inject.Inject
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.game.entity.Player

/**
 * Character-data segment carrying the login-time membership row plus the company's cached state.
 * The load runs inside the account-load transaction, so membership and tree state arrive atomically
 * with the character's own varps.
 */
public data class CompanyCharacterData(
    public val membership: CompanyMembership?,
    public val company: CompanyState?,
) : CharacterDataStage.Segment

public class CompanyCharacterApplier @Inject constructor(private val service: CompanyService) :
    CharacterDataStage.Applier<CompanyCharacterData> {
    override fun apply(player: Player, data: CompanyCharacterData) {
        service.restore(player, data.membership, data.company)
    }
}

public class CompanyCharacterPipeline
@Inject
constructor(private val applier: CompanyCharacterApplier, private val service: CompanyService) :
    CharacterDataStage.Pipeline {
    override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {
        val repository = CompanyRepository(connection)
        val membership = repository.loadMembership(metadata.characterId)
        val company = membership?.let { repository.loadCompany(it.companyId) }
        metadata.add(applier, CompanyCharacterData(membership, company))
    }

    override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
        service.saveCompanyState(connection, player)
    }
}
