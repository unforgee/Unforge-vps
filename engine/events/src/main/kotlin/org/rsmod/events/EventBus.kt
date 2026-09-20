package org.rsmod.events

public class EventBus(
    public val unbound: UnboundEventMap = UnboundEventMap(),
    public val keyed: KeyedEventMap = KeyedEventMap(),
    public val suspend: SuspendEventMap = SuspendEventMap(),
) {
    public fun <T : UnboundEvent> publish(event: T): Boolean {
        val actions = unbound[event::class.java] ?: return false
        for (action in actions) {
            action(event)
        }
        return true
    }

    public fun <T : UnboundEvent> subscribeUnbound(type: Class<T>, action: T.() -> Unit) {
        unbound.add(type, action)
    }

    public fun <T : KeyedEvent> publish(event: T): Boolean {
        val actions = keyed[event::class.java, event.id] ?: return false
        for (action in actions) {
            action(event)
        }
        return true
    }

    public fun <T : KeyedEvent> subscribeKeyed(type: Class<T>, id: Long, action: T.() -> Unit) {
        keyed.add(type, id, action)
    }

    public suspend fun <R, T : SuspendEvent<R>> publish(receiver: R, event: T): Boolean {
        val action = suspend[event::class.java, event.id] ?: return false
        action(receiver, event)
        return true
    }

    public fun <R, T : SuspendEvent<R>> subscribeSuspend(
        type: Class<T>,
        id: Long,
        action: suspend R.(T) -> Unit,
    ) {
        val previous = suspend.putIfAbsent(type, id, action)
        if (previous != null) {
            // Component child indices drift across revisions; allow the latest handler to win so
            // bootstrap can proceed while symbols/hashes are brought current.
            suspend.put(type, id, action)
        }
    }

    public fun <T : SuspendEvent<*>> contains(type: Class<T>, key: Long): Boolean {
        return suspend.contains(type, key)
    }

    public fun <T : SuspendEvent<*>> contains(type: Class<T>, key: Int): Boolean {
        return suspend.contains(type, key.toLong())
    }

    public companion object {
        public fun composeLongKey(high: Int, low: Int): Long {
            return (high.toLong() shl 32) or low.toLong()
        }
    }
}
