package org.rsmod.content.interfaces.skill.guides.configs

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences

typealias guide_interfaces = SkillGuideInterfaces

typealias guide_components = SkillGuideComponents

object SkillGuideInterfaces : InterfaceReferences() {
    /**
     * Rev 239 renders the guide through `skill_guide_v2` (860). The legacy `skill_guide` (214) is
     * still in the cache, but its clientscripts are not the ones that build the entry list on this
     * revision, and none of its buttons are wired to anything the client reports.
     */
    val skill_guide_v2 = find("skill_guide_v2")
}

object SkillGuideComponents : ComponentReferences() {
    // The buttons that open a guide live on the stats tab (interface 320), not on the guide
    // interface. Each carries op1 and op2 with no client-side `onOp`, so the click is reported to
    // the server.
    val attack = find("stats:attack", 4184249122120322083)
    val strength = find("stats:strength", 3279942110565409805)
    val defence = find("stats:defence", 1342833944159457821)
    val ranged = find("stats:ranged", 8814217576824147803)
    val prayer = find("stats:prayer", 1749022814565288062)
    val magic = find("stats:magic", 2910318112712455196)
    val runecraft = find("stats:runecraft", 3002893077606291851)
    val construction = find("stats:construction", 2640482247835721101)
    val hitpoints = find("stats:hitpoints", 9005298535819123910)
    val agility = find("stats:agility", 2972904928411303875)
    val herblore = find("stats:herblore", 2068597916856391597)
    val thieving = find("stats:thieving", 3379093798817134116)
    val crafting = find("stats:crafting", 2474786787262221838)
    val fletching = find("stats:fletching", 2567361752156058493)
    val slayer = find("stats:slayer", 4242018096908957632)
    val hunter = find("stats:hunter", 7312331077720947543)
    val mining = find("stats:mining", 3570174757812110223)
    val smithing = find("stats:smithing", 2665867746257197945)
    val fishing = find("stats:fishing", 8770129878041406678)
    val cooking = find("stats:cooking", 3072056616663028186)
    val firemaking = find("stats:firemaking", 9176318748447236919)
    val woodcutting = find("stats:woodcutting", 8272011736892324641)
    val farming = find("stats:farming", 4120100676970717860)

    /**
     * The only component on interface 860 that carries an op. Everything else on the interface is a
     * layer or a list that `skill_guide_v2_rebuild` populates client-side, so there are no
     * per-skill or per-subsection buttons for the server to bind.
     */
    val close_button = find("skill_guide_v2:close")
}
