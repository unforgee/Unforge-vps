package org.rsmod.api.account.character.main

import java.time.LocalDateTime
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.game.difficulty.Difficulty
import org.rsmod.game.ironman.GameMode
import org.rsmod.game.ironman.GroupRank
import org.rsmod.game.ironman.HardcoreStatus

public data class CharacterAccountData(
    val realm: Int,
    val accountId: Int,
    val characterId: Int,
    val loginName: String,
    val displayName: String?,
    val hashedPassword: String,
    val email: String?,
    val members: Boolean,
    val modLevel: String?,
    val twofaEnabled: Boolean,
    val twofaSecret: String?,
    val twofaLastVerified: LocalDateTime?,
    val knownDevice: Int?,
    val worldId: Int?,
    val coordX: Int,
    val coordZ: Int,
    val coordLevel: Int,
    val varps: Map<Int, Int>,
    val createdAt: LocalDateTime?,
    val lastLogin: LocalDateTime?,
    val lastLogout: LocalDateTime?,
    val mutedUntil: LocalDateTime?,
    val bannedUntil: LocalDateTime?,
    val runEnergy: Int,
    val xpRate: Double,
    // Server-authoritative game mode state. `gameMode == null` + `gameModeSelected == false`
    // means the account has not yet completed the first-login selection.
    val gameMode: GameMode?,
    val gameModeSelected: Boolean,
    val gameModeSelectedAt: LocalDateTime?,
    // Server-authoritative difficulty tier. `difficulty == null` + `difficultySelected == false`
    // means the account has not yet completed the first-login difficulty selection.
    val difficulty: Difficulty?,
    val difficultySelected: Boolean,
    val difficultySelectedAt: LocalDateTime?,
    val hardcoreStatus: HardcoreStatus,
    val hardcoreDeathCount: Int,
    val groupId: Int?,
    val groupRank: GroupRank?,
    val groupJoinedAt: LocalDateTime?,
    val groupSettingsVersion: Int,
) : CharacterDataStage.Segment {
    // Do not include sensitive fields (e.g., password hash, 2fa secret, known device).
    override fun toString(): String =
        "AccountData(" +
            "realm=$realm, " +
            "accountId=$accountId, " +
            "characterId=$characterId, " +
            "loginName=$loginName, " +
            "displayName=$displayName, " +
            "email=$email, " +
            "members=$members, " +
            "modLevel='$modLevel', " +
            "twofaEnabled=$twofaEnabled, " +
            "twofaLastVerified=$twofaLastVerified, " +
            "worldId=$worldId, " +
            "coordX=$coordX, " +
            "coordZ=$coordZ, " +
            "coordLevel=$coordLevel, " +
            "createdAt=$createdAt, " +
            "lastLogin=$lastLogin, " +
            "lastLogout=$lastLogout, " +
            "mutedUntil=$mutedUntil, " +
            "bannedUntil=$bannedUntil, " +
            "xpRate=$xpRate, " +
            "gameMode=$gameMode, " +
            "gameModeSelected=$gameModeSelected, " +
            "difficulty=$difficulty, " +
            "difficultySelected=$difficultySelected, " +
            "hardcoreStatus=$hardcoreStatus, " +
            "groupId=$groupId" +
            ")"
}
