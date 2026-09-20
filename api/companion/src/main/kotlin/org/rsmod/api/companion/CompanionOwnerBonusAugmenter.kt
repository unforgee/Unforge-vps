package org.rsmod.api.companion

import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.bonus.WornBonusesAugmenter
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

/**
 * Feeds the companion owner's equipment bonuses into the companion bot's combat pipeline.
 *
 * Companion bots are real [Player] entities, so their attacks already resolve through the shared
 * player combat formulas ([org.rsmod.api.combat.formulas] max-hit, accuracy and damage rolls).
 * Those formulas read [WornBonuses.calculate], which for a bot returns only the companion's own
 * gear bonuses. This augmenter adds the owner's equipment bonuses for the attack type the companion
 * actually uses:
 * - `MELEE` companions inherit the owner's stab/slash/crush attack bonuses and melee strength.
 * - `RANGED` companions inherit the owner's ranged attack and ranged strength bonuses.
 * - `MAGIC` companions inherit the owner's magic attack and magic damage bonuses.
 *
 * Only the attack-relevant fields of [WornBonuses.equipmentBonuses] are merged, so:
 * - wrong-type bonuses never leak in (a magic companion ignores the owner's melee strength),
 * - player-weapon mechanics (Tumeken's shadow multiplier, Dinh's bulwark conversion, Virtus, elite
 *   void) never apply to companions,
 * - there is no double-counting: each bonus value is produced once by the canonical equipment scan,
 *   for exactly one entity (the owner scan and the bot scan read disjoint inventories).
 *
 * The owner's equipment is re-read on every call, so gear swaps mid-combat take effect on the
 * companion's very next attack roll - nothing is cached.
 */
@Singleton
public class CompanionOwnerBonusAugmenter
@Inject
constructor(
    private val registry: CompanionPlayerRegistry,
    private val companions: CompanionService,
    private val players: PlayerList,
    private val wornBonuses: Provider<WornBonuses>,
) : WornBonusesAugmenter {
    override fun augment(player: Player, bonuses: WornBonuses.Bonuses): WornBonuses.Bonuses {
        val companionId = registry.getCompanionId(player) ?: return bonuses
        val ownerId = registry.getOwnerCharacterId(companionId) ?: return bonuses
        val companion =
            companions.owned(ownerId).firstOrNull { it.id == companionId } ?: return bonuses
        val owner = players.firstOrNull { it.characterId.toLong() == ownerId } ?: return bonuses
        val ownerBonuses = wornBonuses.get().equipmentBonuses(owner)
        return merge(companion.attackStyle, bonuses, ownerBonuses)
    }

    public companion object {
        /**
         * Merges the owner's equipment [owner] bonuses into the companion's own [own] bonuses,
         * restricted to the fields that drive [style]-type attacks.
         */
        public fun merge(
            style: CompanionAttackStyle,
            own: WornBonuses.Bonuses,
            owner: WornBonuses.Bonuses,
        ): WornBonuses.Bonuses =
            when (style) {
                CompanionAttackStyle.MELEE ->
                    own.copy(
                        offStab = own.offStab + owner.offStab,
                        offSlash = own.offSlash + owner.offSlash,
                        offCrush = own.offCrush + owner.offCrush,
                        meleeStr = own.meleeStr + owner.meleeStr,
                    )
                CompanionAttackStyle.RANGED ->
                    own.copy(
                        offRange = own.offRange + owner.offRange,
                        rangedStr = own.rangedStr + owner.rangedStr,
                    )
                CompanionAttackStyle.MAGIC ->
                    own.copy(
                        offMagic = own.offMagic + owner.offMagic,
                        magicDmg = own.magicDmg + owner.magicDmg,
                    )
            }
    }
}
