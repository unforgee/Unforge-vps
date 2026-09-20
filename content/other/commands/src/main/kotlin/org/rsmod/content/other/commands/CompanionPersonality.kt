package org.rsmod.content.other.commands

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.api.companion.Companion
import org.rsmod.api.companion.CompanionClass
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player

/**
 * Living AI Personality and Dynamic Overhead Banter engine for Unforge Companions.
 *
 * Provides real-time speech reactions overhead and in the chatbox across major combat and
 * adventuring milestones.
 */
@Singleton
public class CompanionPersonality @Inject constructor() {

    private val lastSpeechCycle = ConcurrentHashMap<String, Long>()

    private fun canSpeak(key: String, currentCycle: Long, cooldownCycles: Long): Boolean {
        val last = lastSpeechCycle[key] ?: 0L
        if (currentCycle - last >= cooldownCycles) {
            lastSpeechCycle[key] = currentCycle
            return true
        }
        return false
    }

    public fun speak(bot: Player, player: Player, companion: Companion, text: String) {
        bot.say(text)
        player.mes("<col=00b33c>[${companion.name}]: $text</col>")
    }

    public fun sayValuableLoot(
        bot: Player,
        player: Player,
        companion: Companion,
        itemName: String,
        currentCycle: Long,
    ) {
        if (!canSpeak("loot_${companion.id}", currentCycle, 6)) return
        val quips =
            listOf(
                "Jackpot! Stashed a $itemName straight into the pack!",
                "Look at that treasure! Stored the $itemName safely!",
                "Ooh shiny! Nice kill, partner!",
                "That $itemName will fetch a pretty penny back in town!",
            )
        speak(bot, player, companion, quips.random())
    }

    public fun sayFullPack(bot: Player, player: Player, companion: Companion, currentCycle: Long) {
        if (!canSpeak("full_${companion.id}", currentCycle, 15)) return
        val quips =
            listOf(
                "My pack is completely stuffed! Upgrade it so I can carry more!",
                "Oof, I can't carry any more loot! My pack is bursting at the seams!",
                "Pack full! Left those drops on the ground for you!",
            )
        speak(bot, player, companion, quips.random())
    }

    public fun sayEmergencyHealFood(
        bot: Player,
        player: Player,
        companion: Companion,
        itemName: String,
    ) {
        val quips =
            listOf(
                "Eat this $itemName! Stay in the fight!",
                "Hold on! Quick bite of $itemName to keep you breathing!",
                "Don't collapse on me! Chomp down this $itemName!",
            )
        speak(bot, player, companion, quips.random())
    }

    public fun sayEmergencyHealTriage(bot: Player, player: Player, companion: Companion) {
        val quips =
            when (companion.companionClass) {
                CompanionClass.SUPPORT ->
                    listOf(
                        "By the light, receive my blessing! Rise again!",
                        "Mending Light, protect my ally!",
                        "Heavens above, restore this warrior's vigor!",
                    )
                CompanionClass.TANK ->
                    listOf(
                        "Hang in there, I've got your back! Field Triage applied!",
                        "Stand firm! I'm patching you up right now!",
                        "Shielding you! Keep your guard raised!",
                    )
                CompanionClass.DPS ->
                    listOf(
                        "Adrenaline surge! Patch yourself up, let's finish this!",
                        "Quick field dressing! Don't you dare die before the beast does!",
                        "Hold the line! Triage applied, now strike back!",
                    )
            }
        speak(bot, player, companion, quips.random())
    }

    public fun sayWildernessEntered(
        bot: Player,
        player: Player,
        companion: Companion,
        currentCycle: Long,
    ) {
        if (!canSpeak("wild_${companion.id}", currentCycle, 50)) return
        val quips =
            listOf(
                "Watch out! We've entered the Wilderness... keep your eyes peeled!",
                "The air turns cold here. Watch out for skullers and predators!",
                "Danger lurks in these barren wastes. Keep your weapons drawn!",
            )
        speak(bot, player, companion, quips.random())
    }

    public fun sayBossEngaged(
        bot: Player,
        player: Player,
        companion: Companion,
        bossName: String,
        currentCycle: Long,
    ) {
        if (!canSpeak("boss_${companion.id}", currentCycle, 25)) return
        val quips =
            listOf(
                "A formidable $bossName! Let's take it down together!",
                "Stay focused! This $bossName won't go down without a fierce battle!",
                "To victory and glory! Charge the $bossName!",
            )
        speak(bot, player, companion, quips.random())
    }

    public fun sayLevelUp(bot: Player, player: Player, companion: Companion) {
        val quips =
            listOf(
                "I feel immense power surging through me! Level ${companion.level}!",
                "Our bond grows stronger with every victory! Level ${companion.level} reached!",
                "We are becoming unstoppable! Level ${companion.level}!",
            )
        speak(bot, player, companion, quips.random())
    }

    public fun saySummon(bot: Player, player: Player, companion: Companion) {
        val quips =
            listOf(
                "At your side, champion!",
                "Ready for battle and glorious adventure!",
                "Let's hunt!",
            )
        speak(bot, player, companion, quips.random())
    }

    public fun sayDismiss(bot: Player, player: Player, companion: Companion) {
        val quips =
            listOf(
                "Resting until next time, hero.",
                "Call upon me whenever danger arises!",
                "Farewell for now, partner.",
            )
        speak(bot, player, companion, quips.random())
    }
}
