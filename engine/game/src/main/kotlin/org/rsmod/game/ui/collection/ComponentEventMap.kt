package org.rsmod.game.ui.collection

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.game.ui.UserInterface

public class ComponentEventMap(
    private val interfaces: Int2ObjectMap<MutableList<Event>> = Int2ObjectOpenHashMap()
) {
    public operator fun get(type: ComponentType, slot: Int): Long {
        val eventList = interfaces[type.interfaceId]
        if (eventList == interfaces.defaultReturnValue()) {
            return 0L
        }
        var events = 0L
        for (i in 0 until eventList.size) {
            val event = eventList[i]
            if (event.component == type.component && slot >= event.start && slot <= event.end) {
                events = event.events
            }
        }
        return events
    }

    /**
     * Returns `true` when any stored event range covers [slot] for [type], regardless of which
     * events are enabled. An empty event set is still an entry: `ifSetPauseText` and `ifObjbox`
     * send `-1..-1` with no events specifically to _disable_ a component.
     */
    public fun contains(type: ComponentType, slot: Int): Boolean {
        val eventList = interfaces[type.interfaceId]
        if (eventList == interfaces.defaultReturnValue()) {
            return false
        }
        for (i in 0 until eventList.size) {
            val event = eventList[i]
            if (event.component == type.component && slot >= event.start && slot <= event.end) {
                return true
            }
        }
        return false
    }

    public fun add(type: ComponentType, range: IntRange, events: Long) {
        val eventList = interfaces.computeIfAbsent(type.interfaceId) { mutableListOf() }
        val event = Event.from(type.component, range.first, range.last, events)
        eventList.add(event)
    }

    public fun clear(interf: UserInterface) {
        interfaces.remove(interf.id)
    }

    public data class Event(val component: Int, val start: Int, val end: Int, val events: Long) {
        public companion object {
            public fun from(component: Int, start: Int, end: Int, events: Long): Event {
                // `-1` is the "whole component" marker on both ends. Clamping the start to `0`
                // would make a `-1..-1` range unable to match a static (`comsub == -1`) click.
                val clampedStart = if (start == -1) Int.MIN_VALUE else start
                val clampedEnd = if (end == -1) Int.MAX_VALUE else end
                return Event(component, clampedStart, clampedEnd, events)
            }
        }
    }
}
