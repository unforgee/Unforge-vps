package org.rsmod.content.interfaces.gameframe.config

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences

typealias companion_pet_interfaces = CompanionPetInterfaces

typealias companion_pet_components = CompanionPetComponents

object CompanionPetInterfaces : InterfaceReferences() {
    val management = find("companion_pet")
}

object CompanionPetComponents : ComponentReferences() {
    val root = find("companion_pet:root")
    val list_page = find("companion_pet:list_page")
    val list_hint = find("companion_pet:list_hint")
    val slot_buttons = List(4) { find("companion_pet:slot${it + 1}") }
    val slot_texts = List(4) { find("companion_pet:slot${it + 1}_text") }
    val detail_pages = List(4) { find("companion_pet:agent_page${it + 1}") }
    val detail_titles = List(4) { find("companion_pet:agent_title${it + 1}") }
    val detail_contents = List(4) { find("companion_pet:agent_content${it + 1}") }
    val detail_lines =
        List(4) { slot -> List(LINE_POOL) { find("companion_pet:agent_line${slot + 1}_$it") } }
    val scroll_up_buttons = List(4) { find("companion_pet:agent_scrollup${it + 1}") }
    val scroll_down_buttons = List(4) { find("companion_pet:agent_scrolldown${it + 1}") }
    val back_buttons = List(4) { find("companion_pet:agent_back${it + 1}") }
    val activate_buttons = List(4) { find("companion_pet:agent_activate${it + 1}") }
    val resummon_buttons = List(4) { find("companion_pet:agent_resummon${it + 1}") }
    val talent_buttons = List(4) { find("companion_pet:agent_talent${it + 1}") }
    val mode_buttons =
        List(4) { List(3) { mode -> find("companion_pet:agent_mode${it + 1}_$mode") } }
    val spellbook_buttons = List(4) { find("companion_pet:agent_spellbook${it + 1}") }
    val autocast_buttons = List(4) { find("companion_pet:agent_autocast${it + 1}") }

    // One-child wrap holders keep every clickable layer at `childIndex == 0`; the client reports
    // `children[childIndex]` on click, so a button deep in the parent's child list would resolve
    // out of bounds and never emit a packet.
    val slot_wraps = List(4) { find("companion_pet:slot${it + 1}_wrap") }
    val back_wraps = List(4) { find("companion_pet:agent_back${it + 1}_wrap") }
    val scrollup_wraps = List(4) { find("companion_pet:agent_scrollup${it + 1}_wrap") }
    val scrolldown_wraps = List(4) { find("companion_pet:agent_scrolldown${it + 1}_wrap") }
    val activate_wraps = List(4) { find("companion_pet:agent_activate${it + 1}_wrap") }
    val resummon_wraps = List(4) { find("companion_pet:agent_resummon${it + 1}_wrap") }
    val talent_wraps = List(4) { find("companion_pet:agent_talent${it + 1}_wrap") }
    val mode_wraps =
        List(4) { List(3) { mode -> find("companion_pet:agent_mode${it + 1}_${mode}_wrap") } }
    val spellbook_wraps = List(4) { find("companion_pet:agent_spellbook${it + 1}_wrap") }
    val autocast_wraps = List(4) { find("companion_pet:agent_autocast${it + 1}_wrap") }

    const val LINE_POOL: Int = 32
}
