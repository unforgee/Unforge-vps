package org.rsmod.content.skills.smithing.scripts

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.commons.skilling.SkillingRewards
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.stats
import org.rsmod.api.equipment.instance.EquipmentInstanceService
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.bonus.EquipmentTierResolver
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.SkillMulti
import org.rsmod.api.player.ui.SkillMultiOption
import org.rsmod.api.player.ui.SkillMultiVerb
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.skills.smithing.SmithingProductMade
import org.rsmod.content.skills.smithing.configs.SmeltRecipe
import org.rsmod.content.skills.smithing.configs.SmeltingRecipes
import org.rsmod.content.skills.smithing.configs.SmithingEnums
import org.rsmod.content.skills.smithing.configs.SmithingProducts
import org.rsmod.content.skills.smithing.configs.SmithingXp
import org.rsmod.content.skills.smithing.configs.smithing_interfaces
import org.rsmod.content.skills.smithing.configs.smithing_objs
import org.rsmod.content.skills.smithing.configs.smithing_queues
import org.rsmod.content.skills.smithing.configs.smithing_seqs
import org.rsmod.content.skills.smithing.configs.smithing_varbits
import org.rsmod.content.skills.smithing.configs.smithing_varps
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.enums.EnumTypeMap
import org.rsmod.game.enums.EnumTypeMapResolver
import org.rsmod.game.inv.InvObj
import org.rsmod.game.obj.Obj
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.game.type.comp.ComponentTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid

/**
 * Everything the furnace and the anvil actually do, separated from the scripts that bind them.
 *
 * Split out so other content can reuse a smelt or a smith without re-registering handlers on the
 * same locs or interface components - registering a second handler for one key silently replaces
 * the first. Tutorial Island drives its furnace and anvil through here for exactly that reason.
 */
@Singleton
class SmithingActions
@Inject
constructor(
    private val componentTypes: ComponentTypeList,
    private val objTypes: ObjTypeList,
    private val eventBus: EventBus,
    private val equipmentInstances: EquipmentInstanceService,
    private val skillingRewards: SkillingRewards,
    private val objRepo: ObjRepository,
    private val players: PlayerList,
    private val clock: MapClock,
    enumResolver: EnumTypeMapResolver,
) {
    /**
     * Enums 844/845/846 keyed by obj **id**.
     *
     * Not by [ObjType]: `CacheVarLiteral.OBJ` decodes its keys into `HashedObjType`, which is never
     * equal to the `UnpackedObjType` an [ObjTypeList] hands back. Indexing the resolved map with
     * one silently misses and returns the enum's own default - and 846's default is
     * [NEVER_CRAFTABLE], so every slot would read as unsmithable with no error anywhere.
     */
    private val productQuantity: Map<Int, Int> =
        enumResolver[SmithingEnums.product_to_quantity].byObjId()
    private val productBars: Map<Int, Int> =
        enumResolver[SmithingEnums.product_to_bars_required].byObjId()
    private val productLevel: Map<Int, Int> =
        enumResolver[SmithingEnums.product_to_requirement].byObjId()

    /**
     * Every component any tier puts a product on. The grid is ragged - runite has slots bronze does
     * not - so the union is taken rather than a fixed range, and the slots are resolved by packed
     * id because `312:36`..`312:38` have no name in `component.sym`.
     */
    val productSlots: List<ComponentType> by lazy {
        val interfaceId = smithing_interfaces.smithing.id
        SmithingProducts.products.values
            .flatMap { it.keys }
            .distinct()
            .sorted()
            .map { componentTypes.getValue(Component(interfaceId, it).packed) }
    }

    /** Opens the furnace's bar list and runs whatever the player picks. */
    suspend fun openSmeltDialog(access: ProtectedAccess) {
        val recipes = SmeltingRecipes.all
        val options = recipes.map { SkillMultiOption(it.bar, it.label) }
        val remembered = access.vars[smithing_varps.make_quantity].coerceIn(1, SkillMulti.MAX_COUNT)

        val selection =
            access.skillMultiDialog(
                title = "What would you like to smelt?",
                options = options,
                verb = SkillMultiVerb.Smelt,
                maxCount = SkillMulti.MAX_COUNT,
                selectedCount = remembered,
            ) ?: return

        // The client wrote its own copy of this varp when the player picked a quantity; mirror it
        // so the server's idea of "last chosen amount" does not drift from the highlighted button.
        access.vars[smithing_varps.make_quantity] = selection.count
        smelt(access, recipes[selection.index], selection.count)
    }

    private suspend fun smelt(access: ProtectedAccess, recipe: SmeltRecipe, count: Int) {
        if (access.stat(stats.smithing) < recipe.level) {
            access.mes(
                "You need a Smithing level of ${recipe.level} to smelt " +
                    "${recipe.label.lowercase()} bars."
            )
            return
        }

        val request =
            MakeRequest(
                product = recipe.bar,
                produced = 1,
                consumed = recipe.ingredients.associate { it.obj to it.count },
                xp = recipe.xp,
                anim = smithing_seqs.smelt,
                cycles = SMELT_CYCLES,
            )

        if (access.affordableCount(request, count) <= 0) {
            access.mes("You do not have enough ore to smelt ${recipe.label.lowercase()}.")
            return
        }

        startJob(access, request, count)
    }

    /**
     * Opens interface 312 on [preferred]'s page, or on the best bar the player is carrying.
     *
     * Nothing else is sent: `smithing_init` (cs 415) has the client re-run `smithing_setup`
     * whenever varp 210 changes, so writing the bar-type varbit repopulates the whole grid on its
     * own.
     */
    fun openAnvil(access: ProtectedAccess, preferred: ObjType?) {
        if (access.invTotal(access.inv, smithing_objs.hammer) <= 0) {
            access.mes("You need a hammer to work the metal with.")
            return
        }

        val tier = preferred?.let { SmithingProducts.tierByBar[it.id] } ?: bestTier(access)
        if (tier == null) {
            access.mes("You don't have any bars to smith with.")
            return
        }

        access.vars[smithing_varbits.bar_type] = tier
        access.ifOpenMainModal(smithing_interfaces.smithing)
    }

    /**
     * OSRS opens the page for the best bar the player is carrying, so a full inventory of runite
     * does not land on the bronze grid. Bars the player cannot yet smith anything from are skipped,
     * which keeps a stray high-tier bar from opening a page with every slot greyed out.
     */
    private fun bestTier(access: ProtectedAccess): Int? {
        val level = access.stat(stats.smithing)
        return SmithingProducts.barByTier.entries
            .sortedByDescending { it.key }
            .firstOrNull { (_, barId) ->
                val bar = objTypes[barId] ?: return@firstOrNull false
                access.invTotal(access.inv, bar) > 0 && anySmithable(barId, level)
            }
            ?.key
    }

    private fun anySmithable(barId: Int, level: Int): Boolean {
        val slots = SmithingProducts.products[barId] ?: return false
        return slots.values.any { productId -> levelFor(productId) <= level }
    }

    private fun levelFor(productId: Int): Int = productLevel[productId] ?: NEVER_CRAFTABLE

    fun setQuantity(access: ProtectedAccess, count: Int) {
        access.vars[smithing_varps.make_quantity] = count.coerceIn(1, SkillMulti.MAX_COUNT)
    }

    suspend fun askQuantity(access: ProtectedAccess) {
        val count = access.countDialog("Enter amount:")
        setQuantity(access, count.coerceAtLeast(1))
    }

    /** Handles a click on one of interface 312's item slots. */
    suspend fun smithSlot(access: ProtectedAccess, component: Int) {
        val tier = access.vars[smithing_varbits.bar_type]
        val barId = SmithingProducts.barByTier[tier] ?: return
        val bar = objTypes[barId] ?: return
        // A slot the current tier does not fill is a click on an empty square; the client draws
        // nothing there, so say nothing.
        val productId = SmithingProducts.products[barId]?.get(component) ?: return
        val product = objTypes[productId] ?: return

        if (access.invTotal(access.inv, smithing_objs.hammer) <= 0) {
            access.mes("You need a hammer to work the metal with.")
            return
        }

        val required = levelFor(productId)
        if (access.stat(stats.smithing) < required) {
            access.mes(
                "You need a Smithing level of $required to make ${product.name.lowercase()}."
            )
            return
        }

        val barsPerItem = productBars[productId] ?: 1
        if (access.invTotal(access.inv, bar) < barsPerItem) {
            val plural = if (barsPerItem == 1) "bar" else "bars"
            access.mes("You need $barsPerItem ${bar.name.lowercase()} $plural to make that.")
            return
        }

        // Unreachable in practice - `SmithingConfigTest` pins this table's keys to the tiers
        // `barByTier` offers, and `barId` came out of `barByTier` above. Bailing rather than
        // defaulting is the point: a missing rate must never quietly pay the wrong XP, which is
        // exactly what the flat per-bar constant this replaced did for every tier above bronze.
        val xpPerBar = SmithingXp.perBar[barId] ?: return

        val request =
            MakeRequest(
                product = product,
                produced = productQuantity[productId] ?: 1,
                consumed = mapOf(bar to barsPerItem),
                xp = xpPerBar * barsPerItem,
                anim = smithing_seqs.smith,
                cycles = SMITH_CYCLES,
            )

        // Closes the interface *before* the job is queued: `ifClose` clears weak queues, so
        // queueing first would immediately cancel the run it just started.
        access.ifClose()
        val count = access.vars[smithing_varps.make_quantity].coerceIn(1, SkillMulti.MAX_COUNT)
        startJob(access, request, count)
    }

    /**
     * Plays the first swing and hands the rest to the weak queue. Nothing is produced yet - the
     * first item lands when the queue fires, so the animation always precedes its output.
     */
    private fun startJob(access: ProtectedAccess, request: MakeRequest, count: Int) {
        if (count <= 0) {
            return
        }
        access.anim(request.anim)
        access.weakQueue(smithing_queues.make, request.cycles, MakeJob(request, count))
    }

    /**
     * One iteration, then re-queues itself while anything is still owed.
     *
     * Deliberately silent when it stops early: running out of inputs is the normal way a "make all"
     * ends, and the player can see their own inventory.
     */
    suspend fun continueJob(access: ProtectedAccess, job: MakeJob) {
        val request = job.request
        if (!access.hasInputs(request)) {
            return
        }
        // Consume first, so a full inventory is not a problem: every recipe here frees at least one
        // non-stackable ore or bar slot, and everything that yields more than one per bar (dart
        // tips, arrowtips, nails, bolts) stacks.
        for ((obj, amount) in request.consumed) {
            access.invDel(access.inv, obj, amount)
        }
        // Worn skilling gear: chance to preserve the recipe's first ingredient (e.g. a bar).
        if (skillingRewards.rollChance(access, "SMITHING", SkillingRewards.SMITH_SAVE)) {
            val (savedObj, savedAmount) = request.consumed.entries.first()
            access.invAdd(access.inv, savedObj, savedAmount)
            access.mes("<col=6699ff>Your gear preserves some of the materials.</col>")
        }
        val productType = objTypes[request.product]
        val instanced = request.produced == 1 && equipmentInstances.isInstanceEligible(productType)
        if (instanced) {
            // Wearable products get a rolled equipment instance. The db write is async, so the
            // grant is deferred into its callback - a persistence failure falls back to the plain
            // item and a missing/full inventory drops the product under the player instead.
            val uid = access.player.uid
            val coords = access.player.coords
            equipmentInstances.rollAndPersist(
                productType,
                source = "smithing:${request.product.id}",
                tier = EquipmentTierResolver.resolve(productType),
                onFailure = { deliverSmithingProduct(uid, coords, request, InvObj.NO_INSTANCE) },
            ) { instance ->
                deliverSmithingProduct(uid, coords, request, instance.instanceId)
            }
        } else {
            val delivered =
                skillingRewards.grant(access, "SMITHING", request.product, request.produced)
            if (!delivered) {
                for ((obj, amount) in request.consumed) {
                    access.invAdd(access.inv, obj, amount)
                }
                access.mes("You don't have enough inventory space to hold that item.")
                return
            }
        }
        if (instanced) {
            // Instanced products bypass `grant`, so the FashionScape roll happens here.
            skillingRewards.bonusDrop(access, "SMITHING")
        }
        access.statAdvance(stats.smithing, request.xp)
        eventBus.publish(SmithingProductMade(access.player, request.product, request.produced))

        val remaining = job.remaining - 1
        if (remaining > 0 && access.hasInputs(request)) {
            access.anim(request.anim)
            access.weakQueue(smithing_queues.make, request.cycles, MakeJob(request, remaining))
        }
    }

    /**
     * Deferred grant for instanced products: into the inventory when the player is still online
     * with space, otherwise onto the ground where they stood.
     */
    private fun deliverSmithingProduct(
        uid: PlayerUid,
        coords: CoordGrid,
        request: MakeRequest,
        instanceId: Long,
    ) {
        val player = uid.resolve(players)
        if (player != null) {
            val added =
                player.invAdd(
                    player.inv,
                    request.product,
                    request.produced,
                    instanceId = instanceId,
                    strict = false,
                )
            if (added.success) {
                return
            }
        }
        val invObj = InvObj(request.product, count = request.produced, instanceId = instanceId)
        val obj =
            if (player != null) {
                Obj.fromOwner(player, coords, invObj)
            } else {
                Obj.fromServer(clock, coords, invObj)
            }
        objRepo.add(obj, player?.lootDropDuration ?: constants.lootdrop_duration)
    }

    private fun EnumTypeMap<ObjType, Int>.byObjId(): Map<Int, Int> = associate { (key, value) ->
        key.id to (value ?: NEVER_CRAFTABLE)
    }

    private companion object {
        /** Enum 846's default. The client treats it as "this slot is not craftable at all". */
        private const val NEVER_CRAFTABLE = Int.MAX_VALUE

        /** Ticks per bar, matching the length of `human_fai_furnace`. */
        private const val SMELT_CYCLES = 5

        /** Ticks per item, matching the length of `human_smithing`. */
        private const val SMITH_CYCLES = 5
    }
}
