package org.rsmod.content.skills.cooking.scripts

import jakarta.inject.Inject
import org.rsmod.api.commons.skilling.SkillingRewards
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.SkillMultiVerb
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.skills.cooking.configs.COOK_CYCLES
import org.rsmod.content.skills.cooking.configs.Cookable
import org.rsmod.content.skills.cooking.configs.CookingLocs
import org.rsmod.content.skills.cooking.configs.CookingRecipes
import org.rsmod.content.skills.cooking.configs.RANGE_BURN_OFFSET
import org.rsmod.content.skills.cooking.configs.cooking_seqs
import org.rsmod.content.skills.core.MakeIngredient
import org.rsmod.content.skills.core.MakeRecipe
import org.rsmod.content.skills.core.MakeRequest
import org.rsmod.content.skills.core.SkillMakeActions
import org.rsmod.content.skills.core.affordableCount
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.synth.SynthTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Cooking: raw food on a range or an open fire, through the generic make menu.
 *
 * Both halves of the interface are the same menu. Left-clicking a range (`Cook`) opens it with
 * everything the player is carrying, and using raw food on a fire opens the same list - which is
 * what the game's own range menu does, and it keeps one code path for levels, burn rolls and
 * quantity.
 *
 * Burn chance is the module's own model:
 * ```
 * (stopBurn - level) / (stopBurn - level + 10)
 * ```
 *
 * with a range treated as [RANGE_BURN_OFFSET] levels above the food's fire stop-burn, then reduced
 * by worn skilling gear (`COOK_SUCCESS`) and the Gourmet perk. At or above `stopBurn` the chance is
 * exactly zero, so a 99 never burns a shrimp.
 */
class CookingScript
@Inject
constructor(
    private val locTypes: LocTypeList,
    private val objTypes: ObjTypeList,
    private val synthTypes: SynthTypeList,
    private val perks: PerkService,
    private val skillingRewards: SkillingRewards,
    private val make: SkillMakeActions,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (loc in locTypes.values) {
            if (!CookingLocs.matches(loc)) {
                continue
            }
            val isFire = CookingLocs.isFire(loc)
            onOpLoc1(loc) { openCookMenu(this, isFire) }
            onOpLocU(loc) { event ->
                val used = objTypes[event.objType]
                if (CookingRecipes.byRaw.containsKey(used.id)) {
                    openCookMenu(this, isFire)
                }
            }
        }
    }

    /**
     * Everything the player is carrying that this loc can cook, as a menu.
     *
     * Foods above the player's level are left out of the list rather than shown greyed out,
     * matching the client's own range menu; if that leaves nothing at all, the level requirement is
     * spelled out instead of opening an empty dialog.
     */
    private suspend fun openCookMenu(access: ProtectedAccess, isFire: Boolean) {
        val level = access.stat(stats.cooking)
        val carried = CookingRecipes.all.filter { access.invTotal(access.inv, it.raw) > 0 }
        if (carried.isEmpty()) {
            access.mes("You need something to cook.")
            return
        }
        val cookable = carried.filter { level >= it.level }
        if (cookable.isEmpty()) {
            access.mes("You need a Cooking level of ${carried.first().level} to cook this.")
            return
        }

        val recipes = cookable.map { cookableRecipe(it) }
        val selection =
            make.openMakeMenu(access, COOK_TITLE, recipes, verb = SkillMultiVerb.Cook) ?: return
        val cook = CookingRecipes.byCooked[selection.recipe.product.id] ?: return
        if (level < cook.level) {
            access.mes("You need a Cooking level of ${cook.level} to cook this.")
            return
        }

        val rawName = objTypes[cook.raw].name.lowercase().removePrefix("raw ")
        val reductionBps =
            skillingRewards.value(access.player, COOKING, SkillingRewards.COOK_SUCCESS) +
                perks.burnReductionBps(access.player)
        val request =
            MakeRequest(
                product = cook.cooked,
                consumed = mapOf(cook.raw to 1),
                skill = COOKING,
                stat = stats.cooking,
                xp = cook.xp,
                anim = if (isFire) cooking_seqs.fire else cooking_seqs.range,
                sound = synthTypes[COOK_SYNTH],
                cycles = COOK_CYCLES,
                failureProduct = cook.burnt,
                failureChanceBps = { current ->
                    burnChanceBps(cook, current, isFire, reductionBps)
                },
                successMessage = "You successfully cook the $rawName.",
                failureMessage = "You accidentally burn the $rawName.",
            )
        make.start(access, request, access.affordableCount(request, selection.count))
    }

    /** The menu row for [cook]: cooked obj, real name, level and xp. */
    private fun cookableRecipe(cook: Cookable): MakeRecipe =
        MakeRecipe(
            product = cook.cooked,
            label = objTypes[cook.cooked].name,
            level = cook.level,
            xp = cook.xp,
            ingredients = listOf(MakeIngredient(cook.raw, 1)),
        )

    /**
     * Burn chance in basis points for a player at [level], after worn gear and perks.
     *
     * The `+ 10` in the denominator is what keeps the curve from dividing by zero at the food's own
     * requirement, where `stopBurn - level` is at its largest relative value; at the stop-burn
     * level the numerator is zero and the food cannot burn at all.
     */
    private fun burnChanceBps(cook: Cookable, level: Int, isFire: Boolean, reductionBps: Int): Int {
        val stopBurn = if (isFire) cook.stopBurn else cook.stopBurn - RANGE_BURN_OFFSET
        if (level >= stopBurn) {
            return 0
        }
        val span = stopBurn - cook.level + 10
        val chance = (stopBurn - level).toDouble() / span
        val kept =
            (SkillingRewards.ROLL_BOUND - reductionBps.coerceIn(0, SkillingRewards.ROLL_BOUND))
        val reduced = chance * kept / SkillingRewards.ROLL_BOUND
        return (reduced * SkillingRewards.ROLL_BOUND)
            .toInt()
            .coerceIn(0, SkillingRewards.ROLL_BOUND)
    }

    private companion object {
        private const val COOKING = "COOKING"

        private const val COOK_TITLE = "How many would you like to cook?"

        /**
         * The cooking synth, authored as an id.
         *
         * `synth.sym` carries no names in this revision - the old module hard-coded the same id,
         * and this keeps the sound the player already heard for a cook.
         */
        private const val COOK_SYNTH = 2577
    }
}
