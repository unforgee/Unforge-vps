package org.rsmod.api.game.process.npc

import jakarta.inject.Inject
import org.rsmod.api.npc.events.AiTimerEvents
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.npc.UnpackedNpcType

public class AiTimerProcessor @Inject constructor(private val eventBus: EventBus) {
    public fun process(npc: Npc) {
        if (npc.aiTimer <= 0) {
            return
        }
        npc.processTimer()
    }

    private fun Npc.processTimer() {
        aiTimer--

        if (aiTimer > 0) {
            return
        }

        aiTimer = aiTimerStart
        publishEvent()
    }

    private fun Npc.publishEvent(type: UnpackedNpcType = visType) {
        val typeTriggers = eventBus.keyed[AiTimerEvents.Type::class.java, type.id]
        if (!typeTriggers.isNullOrEmpty()) {
            val event = AiTimerEvents.Type(this)
            typeTriggers.forEach { it.invoke(event) }
            return
        }

        if (type.contentGroup != -1) {
            val contentTriggers =
                eventBus.keyed[AiTimerEvents.Content::class.java, type.contentGroup]
            if (!contentTriggers.isNullOrEmpty()) {
                val event = AiTimerEvents.Content(this, type.contentGroup)
                contentTriggers.forEach { it.invoke(event) }
                return
            }
        }

        eventBus.publish(AiTimerEvents.Default(this))
    }
}
