package org.rsmod.api.account.character.main

import jakarta.inject.Inject
import java.time.LocalDateTime
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.realm.Realm
import org.rsmod.game.entity.Player
import org.rsmod.game.ironman.GameMode
import org.rsmod.game.ironman.groupObserverId
import org.rsmod.game.type.mod.ModLevelTypeList
import org.rsmod.map.CoordGrid

public class CharacterAccountApplier
@Inject
constructor(private val modLevelTypes: ModLevelTypeList, private val realm: Realm) :
    CharacterDataStage.Applier<CharacterAccountData> {
    override fun apply(player: Player, data: CharacterAccountData) {
        player.accountId = data.accountId
        player.characterId = data.characterId

        val accountHash = (data.accountId.toLong() shl 32) or data.realm.toLong()
        val userHash = data.loginName.hashCode().toLong()
        player.userId = data.characterId.toLong()
        player.accountHash = accountHash
        player.userHash = userHash

        val uuid = data.characterId.toLong()
        player.uuid = uuid
        player.observerUUID = uuid

        val device = data.knownDevice
        player.lastKnownDevice = device
        player.members = true
        player.username = data.loginName
        player.displayName = data.displayName ?: ""
        player.coords = CoordGrid(data.coordX, data.coordZ, data.coordLevel)
        player.runEnergy = data.runEnergy
        player.xpRate = data.xpRate
        player.globalXpRate = realm.config.globalXpRate
        player.lastLogin = LocalDateTime.now()
        player.vars.backing.putAll(data.varps)
        player.assignModLevel(data.modLevel)
        player.assignGameMode(data)
    }

    private fun Player.assignGameMode(data: CharacterAccountData) {
        gameMode = data.gameMode
        gameModeSelected = data.gameModeSelected
        gameModeSelectedAt = data.gameModeSelectedAt
        difficulty = data.difficulty
        difficultySelected = data.difficultySelected
        difficultySelectedAt = data.difficultySelectedAt
        hardcoreStatus = data.hardcoreStatus
        hardcoreDeathCount = data.hardcoreDeathCount
        groupId = data.groupId
        groupRank = data.groupRank
        groupJoinedAt = data.groupJoinedAt
        groupSettingsVersion = data.groupSettingsVersion

        // Group ironman members share a group-scoped observer id so that owned/private objs
        // (drops, death piles) are visible to and owned by the entire group.
        val group = data.groupId
        if (data.gameMode == GameMode.GROUP_IRONMAN && group != null) {
            observerUUID = groupObserverId(group)
        }
    }

    private fun Player.assignModLevel(modLevelName: String?) {
        val match =
            if (modLevelName != null) {
                modLevelTypes.values.firstOrNull { it.internalName == modLevelName }
            } else {
                null
            }
        modLevel = match ?: modLevelTypes.default()
    }
}
