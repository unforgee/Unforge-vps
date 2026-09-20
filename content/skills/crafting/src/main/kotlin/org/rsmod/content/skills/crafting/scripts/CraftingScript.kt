package org.rsmod.content.skills.crafting.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.SkillMultiVerb
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.skills.core.MakeIngredient
import org.rsmod.content.skills.core.MakeRecipe
import org.rsmod.content.skills.core.MakeRequest
import org.rsmod.content.skills.core.SkillMakeActions
import org.rsmod.content.skills.core.affordableCount
import org.rsmod.content.skills.core.selectRecipes
import org.rsmod.content.skills.crafting.configs.CraftingLocs
import org.rsmod.content.skills.crafting.configs.GEM_CUT_CYCLES
import org.rsmod.content.skills.crafting.configs.GemCut
import org.rsmod.content.skills.crafting.configs.GemCuts
import org.rsmod.content.skills.crafting.configs.LeatherRecipes
import org.rsmod.content.skills.crafting.configs.SEW_CYCLES
import org.rsmod.content.skills.crafting.configs.SPIN_CYCLES
import org.rsmod.content.skills.crafting.configs.SpinningRecipes
import org.rsmod.content.skills.crafting.configs.crafting_objs
import org.rsmod.content.skills.crafting.configs.crafting_seqs
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.synth.SynthTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Crafting: gem cutting, spinning and needlework.
 *
 * Three families, each with the trigger the game uses for it - a chisel on an uncut gem, an item on
 * a spinning wheel, a needle on leather - and each opening the same generic make menu with only the
 * recipes the player can actually do. Every one of them then runs on the shared make loop, so
 * quantities, xp, level gates and interruptions behave identically to Cooking and Smithing.
 *
 * The needle and the chisel are **tools**: they are checked by the trigger that needs them (a
 * chisel is in hand by definition of having used it on a gem) but never consumed, so they never
 * appear in a recipe's ingredient list.
 */
class CraftingScript
@Inject
constructor(
    private val locTypes: LocTypeList,
    private val objTypes: ObjTypeList,
    private val synthTypes: SynthTypeList,
    private val make: SkillMakeActions,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (gem in GemCuts.all) {
            onOpHeldU(crafting_objs.chisel, gem.uncut) { openGemMenu(this) }
        }
        for (opener in LeatherRecipes.openers.values) {
            onOpHeldU(crafting_objs.needle, opener) { openLeatherMenu(this) }
        }
        for (loc in locTypes.values) {
            if (!CraftingLocs.isSpinningWheel(loc)) {
                continue
            }
            onOpLoc1(loc) { openSpinMenu(this) }
            onOpLocU(loc) { event ->
                if (SpinningRecipes.byIngredient.containsKey(objTypes[event.objType].id)) {
                    openSpinMenu(this)
                }
            }
        }
    }

    private suspend fun openGemMenu(access: ProtectedAccess) {
        val level = access.stat(stats.crafting)
        val carried = GemCuts.all.filter { access.invTotal(access.inv, it.uncut) > 0 }
        if (carried.isEmpty()) {
            access.mes("You need an uncut gem to cut.")
            return
        }
        val recipes =
            access.selectRecipes(
                carried.map { gemRecipe(it) },
                level,
                requirementText = "cut a gem",
                skillName = "Crafting",
            ) ?: return
        val selection =
            make.openMakeMenu(access, GEM_TITLE, recipes, verb = SkillMultiVerb.Cut) ?: return
        val gem = GemCuts.byCut[selection.recipe.product.id] ?: return
        if (level < gem.level) {
            access.mes("You need a Crafting level of ${gem.level} to cut this gem.")
            return
        }

        val request =
            MakeRequest(
                product = gem.cut,
                consumed = mapOf(gem.uncut to 1),
                skill = CRAFTING,
                stat = stats.crafting,
                xp = gem.xp,
                anim = crafting_seqs.cut_gem,
                sound = synthTypes[GEM_SYNTH],
                cycles = GEM_CUT_CYCLES,
                successMessage = "You cut the ${objTypes[gem.cut].name.lowercase()}.",
            )
        make.start(access, request, access.affordableCount(request, selection.count))
    }

    private fun gemRecipe(gem: GemCut): MakeRecipe =
        MakeRecipe(
            product = gem.cut,
            label = objTypes[gem.cut].name,
            level = gem.level,
            xp = gem.xp,
            ingredients = listOf(MakeIngredient(gem.uncut, 1)),
        )

    private suspend fun openSpinMenu(access: ProtectedAccess) {
        val level = access.stat(stats.crafting)
        val recipes =
            access.selectRecipes(
                SpinningRecipes.all,
                level,
                requirementText = "spin that",
                skillName = "Crafting",
            ) ?: return
        val selection =
            make.openMakeMenu(access, SPIN_TITLE, recipes, verb = SkillMultiVerb.Spin) ?: return
        val recipe = SpinningRecipes.byProduct[selection.recipe.product.id] ?: return
        if (level < recipe.level) {
            access.mes("You need a Crafting level of ${recipe.level} to spin that.")
            return
        }

        val source = objTypes[recipe.ingredients.first().obj].name.lowercase()
        val product = objTypes[recipe.product].name.lowercase()
        val request =
            MakeRequest.of(
                recipe = recipe,
                skill = CRAFTING,
                stat = stats.crafting,
                anim = crafting_seqs.spin,
                cycles = SPIN_CYCLES,
                successMessage = "You spin the $source into $product.",
            )
        make.start(access, request, access.affordableCount(request, selection.count))
    }

    private suspend fun openLeatherMenu(access: ProtectedAccess) {
        val level = access.stat(stats.crafting)
        val recipes =
            access.selectRecipes(
                LeatherRecipes.all,
                level,
                requirementText = "sew that",
                skillName = "Crafting",
            ) ?: return
        val selection =
            make.openMakeMenu(access, SEW_TITLE, recipes, verb = SkillMultiVerb.Craft) ?: return
        val recipe = recipes.firstOrNull { it.product.id == selection.recipe.product.id } ?: return
        if (level < recipe.level) {
            access.mes("You need a Crafting level of ${recipe.level} to sew that.")
            return
        }

        val request =
            MakeRequest.of(
                recipe = recipe,
                skill = CRAFTING,
                stat = stats.crafting,
                anim = crafting_seqs.sew,
                cycles = SEW_CYCLES,
                successMessage = "You make the ${objTypes[recipe.product].name.lowercase()}.",
            )
        make.start(access, request, access.affordableCount(request, selection.count))
    }

    private companion object {
        private const val CRAFTING = "CRAFTING"

        private const val GEM_TITLE = "What would you like to make?"

        private const val SPIN_TITLE = "What would you like to spin?"

        private const val SEW_TITLE = "What would you like to make?"

        /** Authored as an id: `synth.sym` carries no names in this revision. */
        private const val GEM_SYNTH = 2586
    }
}
