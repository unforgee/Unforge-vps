package org.rsmod.api.game.process.npc

import jakarta.inject.Inject
import org.rsmod.api.npc.events.AiQueueEvents
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.queue.AiQueueType
import org.rsmod.game.type.npc.UnpackedNpcType

public class AiQueueProcessor @Inject constructor(private val eventBus: EventBus) {
    public fun process(npc: Npc) {
        if (npc.pendingAiQueueCycle <= 0) {
            return
        }
        npc.processQueue()
    }

    private fun Npc.processQueue() {
        pendingAiQueueCycle--
        if (pendingAiQueueCycle > 0) {
            return
        }
        val aiQueue = checkNotNull(pendingAiQueue)
        clearPendingAiQueue()
        publishEvent(aiQueue)
    }

    private fun Npc.publishEvent(queue: AiQueueType, type: UnpackedNpcType = visType) {
        val typeTriggers = eventBus.keyed[AiQueueEvents.Type::class.java, type.id]
        if (!typeTriggers.isNullOrEmpty()) {
            val event = AiQueueEvents.Type(this, queue.id)
            typeTriggers.forEach { it.invoke(event) }
            return
        }

        if (type.contentGroup != -1) {
            val contentTriggers =
                eventBus.keyed[AiQueueEvents.Content::class.java, type.contentGroup]
            if (!contentTriggers.isNullOrEmpty()) {
                val event = AiQueueEvents.Content(this, queue.id, type.contentGroup)
                contentTriggers.forEach { it.invoke(event) }
                return
            }
        }

        eventBus.publish(AiQueueEvents.Default(this, queue.id))
    }
}
