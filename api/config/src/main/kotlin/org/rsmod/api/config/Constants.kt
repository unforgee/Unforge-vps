@file:Suppress("SpellCheckingInspection", "unused", "ConstPropertyName")

package org.rsmod.api.config

typealias constants = Constants

object Constants {
    const val dm_default: String = "Nothing interesting happens."
    const val dm_reach: String = "I can't reach that!"
    const val dm_invspace: String = "You don't have enough inventory space to do that."
    const val dm_busy: String = "Please finish what you are doing first."

    const val dm_take_taken: String = "Too late - it's gone!"
    const val dm_take_invspace: String = "You don't have enough inventory space to hold that item."

    const val cm_pausebutton: String = "Click here to continue"
    const val cm_options: String = "Select an option"
    const val cm_count: String = "Enter amount:"
    const val cm_obj: String = "Select an item:"

    const val lootdrop_duration: Int = 200
    const val shop_default_size: Int = 40

    // Common direction angles for `exactmove`.
    const val em_face_south = 0
    const val em_face_southwest = 256
    const val em_face_west = 512
    const val em_face_northwest = 768
    const val em_face_north = 1024
    const val em_face_northeast = 1280
    const val em_face_east = 1536
    const val em_face_southeast = 1792

    // Common designated spotanim slots.
    const val spotanim_slot_combat: Int = 1

    const val ge_ratelimit_combatkit_uncommon: Int = 15
    const val ge_ratelimit_combatkit_rare: Int = 8

    // Arbitrary value until I get more info on this var.
    const val ge_recalcusers_normal: Int = 25
    // Arbitrary value until I get more info on this var.
    const val ge_recalcusers_low: Int = 10

    const val meslayer_mode_countdialog = 7
    const val meslayer_mode_objsearch = 11
    const val meslayer_mode_objdialog = 14

    const val modal_fixedwidthandheight: Int = 0
    const val modal_infinitewidthandheight: Int = 1

    const val toplevel_combat: Int = 0
    const val toplevel_stats: Int = 1
    const val toplevel_details: Int = 2
    const val toplevel_inventory: Int = 3
    const val toplevel_worn: Int = 4
    const val toplevel_prayer: Int = 5
    const val toplevel_magic: Int = 6
    const val toplevel_account: Int = 8
    const val toplevel_settings: Int = 11

    const val setting_lit_maxhit_min_threshold = 10

    /**
     * The resource-respawn overlay timers, as decoded from `add_overlaytimer_loc` (cs 5474). Its
     * `timer` argument reaches cs 5476, which switches on it to pick the settings gate: `1` runs cs
     * 5482, which reads varbit 13085 `ore_respawn_timer`, and `2` runs cs 5483, which reads varbit
     * 13086 `woodcutting_respawn_timer`. `0` is always-on.
     */
    const val overlay_timer_mining: Int = 1

    const val overlay_timer_woodcutting: Int = 2

    const val combat_default_attackrate = 4
    const val combat_pstaff_attackrate = 4
    const val combat_spell_attackrate = 5
    const val combat_activecombat_delay = 8

    const val npc_immunity_none = 0
    const val npc_venom_partial_immunity = 1
    const val npc_venom_full_immunity = 2

    const val elemental_weakness_wind = 0
    const val elemental_weakness_water = 1
    const val elemental_weakness_earth = 2
    const val elemental_weakness_fire = 3

    const val run_max_energy = 10_000
    const val sa_max_energy = 1000

    const val skullicon_default = 0
    const val skullicon_red = 1
    const val skullicon_highrisk_world = 2
    const val skullicon_forinthry_surge = 3

    const val overhead_protect_from_melee = 0
    const val overhead_protect_from_missiles = 1
    const val overhead_protect_from_magic = 2
    const val overhead_retribution = 3
    const val overhead_smite = 4
    const val overhead_redemption = 5
    const val overhead_protect_from_magic_melee = 6
    const val overhead_protect_from_melee_missiles = 7
    const val overhead_protect_from_melee_magic = 8
    const val overhead_protect_from_all_styles = 9
    const val overhead_wrath = 10
    const val overhead_soulsplit = 11
    const val overhead_deflect_melee = 12
    const val overhead_deflect_missiles = 13
    const val overhead_deflect_magic = 14

    const val dinhs_attackstyle_pummel = 0
    const val dinhs_attackstyle_block = 3

    const val stat_regen_interval = 100
    const val stat_boost_restore_interval = 100
    const val health_regen_interval = 100
    const val spec_regen_interval = 50

    const val bodytype_a = 0
    const val bodytype_b = 1

    const val setpos_abs_top = 0
    const val setpos_abs_left = 0
    const val setpos_abs_centre = 1
    const val setpos_abs_bottom = 2
    const val setpos_abs_right = 2

    const val setsize_abs = 0
    const val setsize_minus = 1

    const val settextalign_top = 0
    const val settextalign_left = 0
    const val settextalign_centre = 1
    const val settextalign_bottom = 2
    const val settextalign_right = 2

    const val mod_clientcode_player = 0
    const val mod_clientcode_pmod = 1
    const val mod_clientcode_jmod = 2

    /*
     * Index contract for the client's `modIcons` array (`<img=N>` tags and chat crown byte).
     * The vanilla sprite group carries 60 icons (0..59); the client splice-installs the
     * Unforge rank icons starting at `rankicon_donator_base`, laid out as:
     *   60..66 - donator badges, one per tier (sapphire..zenyte)
     *   67..73 - pmod crown combined with each donator badge
     *   74..80 - jmod crown combined with each donator badge
     * The client-side installer lives in `ChatIconManager.installUnforgeRankIcons`; keep both
     * sides in sync when changing these ranges.
     */
    const val rankicon_donator_base = 60
    const val rankicon_donator_count = 7
    const val rankicon_combined_base = rankicon_donator_base + rankicon_donator_count

    fun isOverhead(icon: Int?): Boolean =
        icon in overhead_protect_from_melee..overhead_deflect_magic
}
