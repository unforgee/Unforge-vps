package org.rsmod.api.player.perk

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.stats.levelmod.InvisibleLevelMod
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule

/**
 * `Instinct` perk: grants invisible gathering levels that feed into `statRandom` success rolls in
 * the Woodcutting and Mining scripts.
 */
public class PerkWoodcuttingBoost @Inject constructor(private val perks: PerkService) :
    InvisibleLevelMod(stats.woodcutting) {
    override fun Player.calculateBoost(): Int = perks.invisibleGatherBoost(this)
}

public class PerkMiningBoost @Inject constructor(private val perks: PerkService) :
    InvisibleLevelMod(stats.mining) {
    override fun Player.calculateBoost(): Int = perks.invisibleGatherBoost(this)
}

public class PerkModule : PluginModule() {
    override fun bind() {
        addSetBinding<InvisibleLevelMod>(PerkWoodcuttingBoost::class.java)
        addSetBinding<InvisibleLevelMod>(PerkMiningBoost::class.java)
        newSetBinding<org.rsmod.api.player.bonus.WornBonusesAugmenter>()
    }
}
