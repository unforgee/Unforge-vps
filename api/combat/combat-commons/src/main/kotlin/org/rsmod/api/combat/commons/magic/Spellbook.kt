package org.rsmod.api.combat.commons.magic

import org.rsmod.api.utils.vars.VarEnumDelegate

public enum class Spellbook(override val varValue: Int) : VarEnumDelegate {
    Standard(0),
    Ancients(1),
    Lunars(2),
    Arceuus(3),

    /**
     * The UnForge PvM support book: endgame self/party healing, PvM damage and defence buffs, and
     * prayer support. Deliberately stronger than the vanilla books because the project's npcs are
     * tuned above vanilla OSRS.
     */
    PvmSupport(4);

    public val isStandard: Boolean
        get() = this == Standard

    public val isAncients: Boolean
        get() = this == Ancients

    public val isLunars: Boolean
        get() = this == Lunars

    public val isArceuus: Boolean
        get() = this == Arceuus

    public val isPvmSupport: Boolean
        get() = this == PvmSupport

    public companion object {
        public operator fun get(id: Int): Spellbook? =
            when (id) {
                Standard.varValue -> Standard
                Ancients.varValue -> Ancients
                Lunars.varValue -> Lunars
                Arceuus.varValue -> Arceuus
                PvmSupport.varValue -> PvmSupport
                else -> null
            }
    }
}
