package org.rsmod.content.pvmprogression.mutation

import java.util.WeakHashMap
import org.rsmod.content.pvmprogression.events.MutationKind
import org.rsmod.content.pvmprogression.events.MutationRarity

/** One applied mutation on an npc. Held in a [WeakHashMap] keyed by the npc. */
data class MutationInstance(val kinds: List<MutationKind>, val rarity: MutationRarity)
