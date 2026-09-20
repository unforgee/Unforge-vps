package org.rsmod.content.pvmsupport

import jakarta.inject.Inject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import org.rsmod.api.area.checker.isWilderness
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.npc.threat.ThreatService
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statBoost
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.support.SupportCombatState
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

/**
 * Casts the PvM support spells and applies their effects.
 *
 * Everything here is server-authoritative: the active spellbook is read from the player's own
 * varbit, the level requirement is re-checked on the server, and the cooldown gate runs before any
 * effect. Healing is capped at [WornBonuses.maximumHitpoints] so it can never overheal, and every
 * buff goes through [SupportCombatState] which only the npc-scoped damage paths read - so nothing
 * here can affect PvP.
 *
 * There is no server-side party/clan roster in this project, so "party" means *friendly players
 * within the spell's radius* (there is no faction system; PvP interactions are out of scope for the
 * first version, which is why all players count as friendly).
 */
public class SupportSpellService
@Inject
constructor(
    private val bonuses: WornBonuses,
    private val instances: EquipmentInstanceRegistry,
    private val players: PlayerList,
    private val threat: ThreatService,
) {
    /**
     * Account names currently on the PvM support book. Keyed by username rather than `Player` so it
     * survives a reconnect, exactly like the cooldowns.
     */
    private val supportEnabled: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** The spellbook the player currently has open, per their own (server-synced) varbit. */
    public fun currentSpellbook(player: Player): Spellbook? =
        Spellbook[player.vars[varbits.spellbook]]

    /**
     * Whether the player has the PvM support book active.
     *
     * This is deliberately **not** read from the `spellbook` varbit: that varbit is only two bits
     * wide (`bits=0..1`), so it can only ever hold the four vanilla books. `Spellbook.PvmSupport`
     * therefore has no cache slot yet and is tracked server-side until the cache carries a fifth
     * book (wider varbit + a `spellbook` enum entry + a tab sprite). Writing `4` to the varbit
     * throws `Varbit overflow`, which used to escape the packet path and stall the player's input.
     */
    public fun isSupportEnabled(player: Player): Boolean = player.username in supportEnabled

    /**
     * Writes the client-visible `spellbook` varbit. Returns `false` (and writes nothing) when the
     * requested book cannot be represented by the varbit, so a caller can never crash the game
     * cycle by asking for an out-of-range value.
     */
    public fun setSpellbook(player: Player, book: Spellbook): Boolean {
        val varbit = varbits.spellbook
        if (book.varValue !in varbit.bits) {
            return false
        }
        VarPlayerIntMapSetter.set(player, varbit, book.varValue)
        // Any real book switch ends support mode; the two are mutually exclusive.
        supportEnabled.remove(player.username)
        return true
    }

    /**
     * Activates the PvM support book server-side. Validates the same level requirement as casting
     * so the caller can report a real reason instead of silently doing nothing.
     */
    public fun enableSupport(player: Player) {
        supportEnabled.add(player.username)
    }

    public fun canCast(player: Player, spell: SupportSpell): CastRejection? {
        if (player.hitpoints <= 0) {
            return CastRejection.Dead
        }
        if (!isSupportEnabled(player)) {
            return CastRejection.WrongSpellbook
        }
        if (!SupportSpellUnlocks.isUnlocked(player, spell)) {
            return CastRejection.Locked
        }
        if (player.magicLvl < spell.levelReq) {
            return CastRejection.LevelTooLow
        }
        if (!SupportCooldowns.isReady(player, spell)) {
            return CastRejection.OnCooldown
        }
        return null
    }

    /**
     * Attempts a cast. Returns `true` only when the spell actually fired, so the caller can decide
     * whether to award xp/consume runes.
     */
    public fun cast(access: ProtectedAccess, spell: SupportSpell): Boolean {
        val player = access.player
        when (val rejection = canCast(player, spell)) {
            null -> Unit
            CastRejection.Dead -> {
                player.mes("You can't cast that while dead.")
                return false
            }
            CastRejection.WrongSpellbook -> {
                player.mes("You need the PvM support book active (::codex pvm) to cast that.")
                return false
            }
            CastRejection.Locked -> {
                player.mes("You haven't learnt ${spell.displayName} yet - study its scroll first.")
                return false
            }
            CastRejection.LevelTooLow -> {
                player.mes("Your Magic level is not high enough for this spell.")
                return false
            }
            CastRejection.OnCooldown -> {
                val seconds = SupportCooldowns.remainingTicks(player, spell) * 6 / 10
                player.mes("${spell.displayName} is on cooldown (${seconds}s).")
                return false
            }
        }

        apply(player, spell)
        SupportCooldowns.start(player, spell, bonuses, instances)
        player.mes("<col=66ccff>${spell.displayName}</col>")
        return true
    }

    private fun apply(player: Player, spell: SupportSpell) {
        when (val effect = spell.effect) {
            is SupportEffect.SelfHeal -> heal(player, player, effect.percent)

            is SupportEffect.PartyHeal -> {
                val allies = party(player, effect.radius)
                heal(player, player, effect.percent)
                var healed = 0
                for (ally in allies) {
                    if (healed >= effect.maxTargets) {
                        break
                    }
                    heal(player, ally, effect.percent)
                    healed++
                }
            }

            is SupportEffect.HealAndShield -> {
                heal(player, player, effect.healPercent)
                val shield = bonuses.maximumHitpoints(player) * effect.shieldPercent / 100
                SupportCombatState.grantShield(player, shield, effect.shieldTicks)
                threat.onShield(player, listOf(player), shield)
            }

            is SupportEffect.DamageBuff -> {
                SupportCombatState.applyDamageBuff(player, effect.selfDamageBps, effect.ticks)
                SupportCombatState.applyAccuracyBuff(player, effect.selfAccuracyBps, effect.ticks)
                for (ally in party(player, effect.radius)) {
                    SupportCombatState.applyDamageBuff(ally, effect.partyDamageBps, effect.ticks)
                    SupportCombatState.applyAccuracyBuff(ally, effect.selfAccuracyBps, effect.ticks)
                }
            }

            is SupportEffect.DefenceBuff -> {
                SupportCombatState.applyIncomingReduction(
                    player,
                    effect.selfReductionBps,
                    effect.ticks,
                )
                player.statBoost(stats.defence, constant = 0, percent = effect.defencePercent)
                player.statBoost(stats.magic, constant = 0, percent = effect.magicDefencePercent)
                player.statBoost(stats.ranged, constant = 0, percent = effect.rangedDefencePercent)
                val allies = party(player, effect.radius)
                for (ally in allies) {
                    SupportCombatState.applyIncomingReduction(
                        ally,
                        effect.partyReductionBps,
                        effect.ticks,
                    )
                }
                // Protection threat: incoming-damage reduction granted to the caster and allies.
                val protectedPoints =
                    bonuses.maximumHitpoints(player) * effect.selfReductionBps / 10_000
                threat.onShield(player, allies + player, protectedPoints)
            }

            is SupportEffect.RestorePrayer -> {
                val base = player.statBase(stats.prayer)
                val amount = base * effect.percent / 100
                if (amount > 0) {
                    player.statHeal(stats.prayer, constant = amount, percent = 0)
                }
            }

            is SupportEffect.LifeSiphon -> {
                heal(player, player, effect.healPercent)
                SupportCombatState.applyDamageBuff(player, effect.selfDamageBps, effect.ticks)
                SupportCombatState.applyAccuracyBuff(player, effect.selfAccuracyBps, effect.ticks)
            }
        }
    }

    /**
     * Heals [target] by [percent] of their maximum hitpoints, never above that maximum. Targets are
     * healed at most once per cast because each [Player] appears once in [PlayerList].
     */
    private fun heal(caster: Player, target: Player, percent: Int) {
        if (percent <= 0 || target.hitpoints <= 0) {
            return
        }
        val max = bonuses.maximumHitpoints(target)
        val missing = (max - target.hitpoints).coerceAtLeast(0)
        val restored = minOf(max * percent / 100, missing)
        if (restored > 0) {
            target.statAdd(stats.hitpoints, constant = restored, percent = 0)
            // Healing threat goes to every npc engaged with the healed ally or the caster.
            threat.onHeal(caster, listOf(target), restored)
        }
    }

    /**
     * Friendly players within [radius] tiles on the same plane, caster excluded.
     *
     * The wilderness is excluded outright: the first version of the support book is PvM-only, so a
     * player standing in a PvP area must never receive a support heal or buff from it.
     */
    private fun party(caster: Player, radius: Int): List<Player> {
        val result = ArrayList<Player>(8)
        for (other in players) {
            if (other === caster || other.hitpoints <= 0) {
                continue
            }
            if (other.coords.level != caster.coords.level || other.coords.isWilderness()) {
                continue
            }
            val dx = abs(other.coords.x - caster.coords.x)
            val dz = abs(other.coords.z - caster.coords.z)
            if (dx > radius || dz > radius) {
                continue
            }
            result += other
        }
        return result
    }

    /** Why a cast was refused; `null` means it is allowed. */
    public enum class CastRejection {
        Dead,
        WrongSpellbook,
        Locked,
        LevelTooLow,
        OnCooldown,
    }
}
