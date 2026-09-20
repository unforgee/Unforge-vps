package org.rsmod.api.equipment.instance

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Singleton
public class EquipmentInstanceRegistry @Inject constructor() {
    private val values = ConcurrentHashMap<Long, EquipmentInstance>()

    public operator fun get(id: Long): EquipmentInstance? = values[id]

    public fun put(value: EquipmentInstance) {
        values[value.instanceId] = value
    }

    public fun remove(id: Long) {
        values.remove(id)
    }
}
