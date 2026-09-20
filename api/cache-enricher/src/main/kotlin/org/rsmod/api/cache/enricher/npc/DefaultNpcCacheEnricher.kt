package org.rsmod.api.cache.enricher.npc

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.inject.Inject
import kotlin.math.round
import org.rsmod.api.config.aliases.ParamCategory
import org.rsmod.api.config.aliases.ParamInt
import org.rsmod.api.config.aliases.ParamProj
import org.rsmod.api.config.aliases.ParamSeq
import org.rsmod.api.config.aliases.ParamSpot
import org.rsmod.api.config.aliases.ParamSynth
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.categories
import org.rsmod.api.config.refs.params
import org.rsmod.api.parsers.toml.Toml
import org.rsmod.api.type.script.dsl.NpcPluginBuilder
import org.rsmod.api.type.symbols.name.NameMapping
import org.rsmod.api.utils.io.InputStreams
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.game.type.proj.ProjAnimTypeList
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.game.type.spot.SpotanimTypeList
import org.rsmod.game.type.synth.SynthType

public class DefaultNpcCacheEnricher
@Inject
constructor(
    @Toml private val mapper: ObjectMapper,
    private val nameMapping: NameMapping,
    private val seqTypes: SeqTypeList,
    private val spotTypes: SpotanimTypeList,
    private val projAnimTypes: ProjAnimTypeList,
) : NpcCacheEnricher {
    private val names: Map<String, Int>
        get() = nameMapping.npcs

    override fun generate(): List<UnpackedNpcType> {
        val external = loadExternalConfigs()
        return external.map { it.toCacheType() }
    }

    private fun loadExternalConfigs(): List<ExternalNpcConfig> {
        val input = InputStreams.readAllBytes<DefaultNpcCacheEnricher>("npcs.toml")
        val type = object : TypeReference<Map<String, List<ExternalNpcConfig>>>() {}
        val map = mapper.readValue(input, type)
        val configs =
            map[CONFIG_MAP_KEY] ?: error("Could not extract `${CONFIG_MAP_KEY}` value from input.")
        return configs
    }

    private fun ExternalNpcConfig.toCacheType(): UnpackedNpcType {
        val id = id ?: names[npc] ?: error("Mapping with name not found: $npc")
        val builder = NpcPluginBuilder(npc ?: "npc_${this.id}")
        return builder.apply(this).build(id)
    }

    private fun NpcPluginBuilder.apply(config: ExternalNpcConfig): NpcPluginBuilder {
        desc = config.examine
        respawnRate = config.respawnRate
        putSeq(config.attackAnim, params.attack_anim)
        putSeq(config.defendAnim, params.defend_anim)
        putSeq(config.deathAnim, params.death_anim)
        putSynth(config.attackSound, params.attack_sound)
        putSynth(config.defendSound, params.defend_sound)
        putSynth(config.deathSound, params.death_sound)
        putInt(config.attackMelee, params.attack_melee)
        putInt(config.defenceLight, params.defence_light)
        putInt(config.defenceStandard, params.defence_standard)
        putInt(config.defenceHeavy, params.defence_heavy)
        putInt(config.poisonImmunity, params.poison_immunity)
        putInt(config.cannonImmunity, params.cannon_immunity)
        putInt(config.thrallImmunity, params.thrall_immunity)
        putInt(config.burnImmunity, params.burn_immunity)
        putInt(config.slayerReq, params.slayer_levelrequire)
        putSlayerXpMod(config.slayerXp, params.slayer_experience)
        putCombatXpMod(config.bonusXp, params.npc_com_xp_multiplier)
        putAttackType(config.attackType, params.npc_attack_type)
        putInt(config.attackStyle, params.npc_attack_style)
        putInt(config.attackRanged, params.attack_ranged)
        putInt(config.rangedStrength, params.ranged_strength)
        putInt(config.attackMagic, params.attack_magic)
        putInt(config.magicDamageBonus, params.npc_magic_damage_bonus)
        putProjAnim(config.projAnim, params.proj_type)
        putSpot(config.projTravel, params.proj_travel)
        putSpot(config.projLaunch, params.proj_launch)
        putElementalWeakness(config.elementalWeaknessType, config.elementalWeaknessPercent)
        putVenomImmunity(config.venomImmunity)
        return this
    }

    private fun NpcPluginBuilder.putSeq(anim: Int?, paramType: ParamSeq) {
        val id = anim ?: return
        val seq = seqTypes.getValue(id)
        param[paramType] = seq.toHashedType()
    }

    private fun NpcPluginBuilder.putSynth(sound: Int?, paramType: ParamSynth) {
        val id = sound ?: return
        val synth = SynthType(id, "synth_$id")
        param[paramType] = synth
    }

    private fun NpcPluginBuilder.putInt(value: Int?, paramType: ParamInt) {
        if (value != null) {
            param[paramType] = value
        }
    }

    private fun NpcPluginBuilder.putInt(value: Boolean?, paramType: ParamInt) {
        if (value != null) {
            param[paramType] = if (value) 1 else 0
        }
    }

    private fun NpcPluginBuilder.putSpot(name: String?, paramType: ParamSpot) {
        val spot = name ?: return
        val id = nameMapping.spotanims[spot] ?: error("Invalid spotanim name: '$spot'")
        param[paramType] = spotTypes.getValue(id).toHashedType()
    }

    private fun NpcPluginBuilder.putProjAnim(name: String?, paramType: ParamProj) {
        val projAnim = name ?: return
        val id = nameMapping.projanims[projAnim] ?: error("Invalid projanim name: '$projAnim'")
        param[paramType] = projAnimTypes.getValue(id).toHashedType()
    }

    private fun NpcPluginBuilder.putCombatXpMod(value: Double?, paramType: ParamInt) {
        if (value != null) {
            // `bonusXp` in npc config file is represented as `+bonus%` (e.g., 7.5 for +7.5%).
            // We scale that percent by 10 and add 1000 to represent the multiplier.
            // Example: 7.5% becomes 1000 + 75 = 1075.
            param[paramType] = 1000 + round(value * 10.0).toInt()
        }
    }

    private fun NpcPluginBuilder.putSlayerXpMod(value: Double?, paramType: ParamInt) {
        if (value != null) {
            param[paramType] = (value * 10).toInt()
        }
    }

    private fun NpcPluginBuilder.putAttackType(value: String?, paramType: ParamCategory) {
        val category =
            when (value?.lowercase()) {
                "stab" -> categories.attacktype_stab
                "slash" -> categories.attacktype_slash
                "crush" -> categories.attacktype_crush
                else -> null
            }
        if (category != null) {
            param[paramType] = category
        }
    }

    private fun NpcPluginBuilder.putElementalWeakness(type: String?, percent: Int?) {
        if (type == null) {
            return
        }
        check(percent != null) { "Unexpected null `percent` value: '$name' (element=$type)" }
        val weaknessType =
            when (type.lowercase()) {
                "air" -> constants.elemental_weakness_wind
                "water" -> constants.elemental_weakness_water
                "earth" -> constants.elemental_weakness_earth
                "fire" -> constants.elemental_weakness_fire
                else -> error("Unexpected weakness type name: $type")
            }
        param[params.elemental_weakness_type] = weaknessType
        param[params.elemental_weakness_percent] = percent
    }

    private fun NpcPluginBuilder.putVenomImmunity(type: String?) {
        if (type == null) {
            return
        }
        val id =
            when (type.lowercase()) {
                "immune" -> constants.npc_venom_full_immunity
                "poisonsinstead" -> constants.npc_venom_partial_immunity
                else -> error("Unexpected venom immunity type name: $type")
            }
        param[params.venom_immunity] = id
    }

    private companion object {
        const val CONFIG_MAP_KEY: String = "config"
    }
}

private data class ExternalNpcConfig(
    val npc: String?,
    val id: Int?,
    val examine: String,
    val attackType: String?,
    val attackStyle: Int?,
    val attackRanged: Int?,
    val rangedStrength: Int?,
    val attackMagic: Int?,
    val magicDamageBonus: Int?,
    val projAnim: String?,
    val projTravel: String?,
    val projLaunch: String?,
    val respawnRate: Int?,
    val bonusXp: Double?,
    val slayerReq: Int?,
    val slayerXp: Double?,
    val attackMelee: Int?,
    val defenceLight: Int?,
    val defenceStandard: Int?,
    val defenceHeavy: Int?,
    val elementalWeaknessType: String?,
    val elementalWeaknessPercent: Int?,
    val freezeResistance: Int?,
    val poisonImmunity: Boolean?,
    val cannonImmunity: Boolean?,
    val thrallImmunity: Boolean?,
    val burnImmunity: Boolean?,
    val venomImmunity: String?,
    val attackAnim: Int?,
    val attackSound: Int?,
    val defendAnim: Int?,
    val defendSound: Int?,
    val deathAnim: Int?,
    val deathSound: Int?,
)
