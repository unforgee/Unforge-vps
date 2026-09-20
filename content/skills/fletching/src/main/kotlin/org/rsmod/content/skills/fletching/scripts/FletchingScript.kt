package org.rsmod.content.skills.fletching.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.skills.core.MakeIngredient
import org.rsmod.content.skills.core.MakeRecipe
import org.rsmod.content.skills.core.MakeRequest
import org.rsmod.content.skills.core.SkillMakeActions
import org.rsmod.content.skills.core.affordableCount
import org.rsmod.content.skills.core.selectRecipes
import org.rsmod.content.skills.fletching.configs.ArrowRecipe
import org.rsmod.content.skills.fletching.configs.ArrowRecipes
import org.rsmod.content.skills.fletching.configs.FLETCH_ARROW_CYCLES
import org.rsmod.content.skills.fletching.configs.FLETCH_CUT_CYCLES
import org.rsmod.content.skills.fletching.configs.FLETCH_FEATHER_CYCLES
import org.rsmod.content.skills.fletching.configs.FLETCH_STRING_CYCLES
import org.rsmod.content.skills.fletching.configs.FeatherRecipes
import org.rsmod.content.skills.fletching.configs.LogCut
import org.rsmod.content.skills.fletching.configs.LogCuts
import org.rsmod.content.skills.fletching.configs.StringRecipe
import org.rsmod.content.skills.fletching.configs.StringRecipes
import org.rsmod.content.skills.fletching.configs.fletching_objs
import org.rsmod.content.skills.fletching.configs.fletching_seqs
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Fletching: knife on logs, string on unstrung bows, feathers on shafts, tips on headless arrows.
 *
 * Every family runs on the shared make loop: a menu lists what the clicked materials can become,
 * and the weak queue repeats the consume -> grant -> advance cycle until the materials run out or
 * the player interrupts. The knife is a **tool** - checked by the trigger, never consumed - while
 * bow strings, feathers and arrowheads are real ingredients and sit in the recipe's cost.
 */
class FletchingScript
@Inject
constructor(private val objTypes: ObjTypeList, private val make: SkillMakeActions) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (log in LogCuts.logs.values) {
            onOpHeldU(fletching_objs.knife, log) { openCutMenu(this, log) }
        }
        for (recipe in StringRecipes.all) {
            onOpHeldU(fletching_objs.bow_string, recipe.unstrung) { openStringMenu(this, recipe) }
        }
        onOpHeldU(fletching_objs.feather, fletching_objs.arrow_shaft) { openFeatherMenu(this) }
        for (recipe in ArrowRecipes.all) {
            onOpHeldU(recipe.tip, fletching_objs.headless_arrow) { openArrowMenu(this, recipe) }
        }
    }

    private suspend fun openCutMenu(access: ProtectedAccess, log: ObjType) {
        val level = access.stat(stats.fletching)
        val recipes =
            access.selectRecipes(
                LogCuts.cutsFor(log.id).map { cutRecipe(it, log) },
                level,
                "carve that",
                skillName = SKILL_NAME,
            ) ?: return
        val selection = make.openMakeMenu(access, CUT_TITLE, recipes) ?: return
        val request =
            MakeRequest.of(
                recipe = selection.recipe,
                skill = FLETCHING,
                stat = stats.fletching,
                anim = fletching_seqs.fletch,
                cycles = FLETCH_CUT_CYCLES,
                successMessage =
                    "You carefully cut the wood into " +
                        objTypes[selection.recipe.product].name.lowercase() +
                        ".",
            )
        make.start(access, request, access.affordableCount(request, selection.count))
    }

    private fun cutRecipe(cut: LogCut, log: ObjType): MakeRecipe =
        MakeRecipe(
            product = cut.product,
            label = cut.label,
            level = cut.level,
            xp = cut.xp,
            ingredients = listOf(MakeIngredient(log, 1)),
            produced = cut.produced,
        )

    private suspend fun openStringMenu(access: ProtectedAccess, recipe: StringRecipe) {
        val level = access.stat(stats.fletching)
        val recipes =
            access.selectRecipes(
                listOf(stringRecipe(recipe)),
                level,
                "string that bow",
                skillName = SKILL_NAME,
            ) ?: return
        val selection = make.openMakeMenu(access, STRING_TITLE, recipes) ?: return
        val request =
            MakeRequest.of(
                recipe = selection.recipe,
                skill = FLETCHING,
                stat = stats.fletching,
                anim = fletching_seqs.string_bow,
                cycles = FLETCH_STRING_CYCLES,
                successMessage = "You add a string to the bow.",
            )
        make.start(access, request, access.affordableCount(request, selection.count))
    }

    private fun stringRecipe(recipe: StringRecipe): MakeRecipe =
        MakeRecipe(
            product = recipe.strung,
            label = recipe.label,
            level = recipe.level,
            xp = recipe.xp,
            ingredients =
                listOf(
                    MakeIngredient(recipe.unstrung, 1),
                    MakeIngredient(fletching_objs.bow_string, 1),
                ),
        )

    private suspend fun openFeatherMenu(access: ProtectedAccess) {
        val level = access.stat(stats.fletching)
        val recipes =
            access.selectRecipes(
                listOf(featherRecipe()),
                level,
                "feather those",
                skillName = SKILL_NAME,
            ) ?: return
        val selection = make.openMakeMenu(access, FEATHER_TITLE, recipes) ?: return
        val request =
            MakeRequest.of(
                recipe = selection.recipe,
                skill = FLETCHING,
                stat = stats.fletching,
                anim = fletching_seqs.fletch,
                cycles = FLETCH_FEATHER_CYCLES,
                successMessage = "You attach feathers to the arrow shafts.",
            )
        make.start(access, request, access.affordableCount(request, selection.count))
    }

    private fun featherRecipe(): MakeRecipe =
        MakeRecipe(
            product = FeatherRecipes.product,
            label = objTypes[FeatherRecipes.product].name,
            level = FeatherRecipes.LEVEL,
            xp = FeatherRecipes.XP_PER_ARROW,
            ingredients =
                listOf(
                    MakeIngredient(FeatherRecipes.ingredient, 1),
                    MakeIngredient(fletching_objs.feather, 1),
                ),
        )

    private suspend fun openArrowMenu(access: ProtectedAccess, recipe: ArrowRecipe) {
        val level = access.stat(stats.fletching)
        val recipes =
            access.selectRecipes(
                listOf(arrowRecipe(recipe)),
                level,
                "make those arrows",
                skillName = SKILL_NAME,
            ) ?: return
        val selection = make.openMakeMenu(access, ARROW_TITLE, recipes) ?: return
        val request =
            MakeRequest.of(
                recipe = selection.recipe,
                skill = FLETCHING,
                stat = stats.fletching,
                anim = fletching_seqs.fletch,
                cycles = FLETCH_ARROW_CYCLES,
                successMessage = "You attach the arrowheads to the arrows.",
            )
        make.start(access, request, access.affordableCount(request, selection.count))
    }

    private fun arrowRecipe(recipe: ArrowRecipe): MakeRecipe =
        MakeRecipe(
            product = recipe.arrow,
            label = recipe.label,
            level = recipe.level,
            xp = recipe.xp * ARROW_SET,
            ingredients =
                listOf(
                    MakeIngredient(recipe.tip, ARROW_SET),
                    MakeIngredient(fletching_objs.headless_arrow, ARROW_SET),
                ),
            produced = ARROW_SET,
        )

    private companion object {
        private const val FLETCHING = "FLETCHING"

        private const val SKILL_NAME = "Fletching"

        /** Arrows are finished in sets: fifteen heads and fifteen headless arrows at a time. */
        private const val ARROW_SET = 15

        private const val CUT_TITLE = "What would you like to make?"

        private const val STRING_TITLE = "What would you like to string?"

        private const val FEATHER_TITLE = "What would you like to make?"

        private const val ARROW_TITLE = "What would you like to make?"
    }
}
