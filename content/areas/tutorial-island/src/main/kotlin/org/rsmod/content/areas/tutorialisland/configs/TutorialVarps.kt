@file:Suppress("SpellCheckingInspection", "unused")

package org.rsmod.content.areas.tutorialisland.configs

import org.rsmod.api.type.refs.varp.VarpReferences

typealias tutorial_varps = TutorialVarps

object TutorialVarps : VarpReferences() {
    val tutorial_2 = find("tutorial_2")
    val tut2_progress_overlay = find("tut2_progress_overlay")
    val tut2_completed = find("tut2_completed")
}
