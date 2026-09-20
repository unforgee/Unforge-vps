package org.rsmod.api.type.builders.interf

import org.rsmod.api.type.builders.HashTypeBuilder
import org.rsmod.game.type.comp.ComponentTypeBuilder
import org.rsmod.game.type.comp.UnpackedComponentType

/**
 * Authors brand-new interface components that are packed into the cache.
 *
 * Each [build] call declares a single component; an "interface" is simply the set of components
 * sharing an interface id. The `internal` name must be a full component symbol - e.g.
 * `dev_hub:root` - present in `.data/symbols/component.sym` (which in turn requires the interface
 * name in `interface.sym`).
 *
 * Components are always authored in the v3 (if3) format. Unlike most other type builders, there is
 * no [org.rsmod.game.type.util.MergeableCacheBuilder] for components: this builder can only author
 * new components, never partially edit vanilla ones.
 */
public abstract class InterfaceBuilder :
    HashTypeBuilder<ComponentTypeBuilder, UnpackedComponentType>() {
    override fun build(internal: String, init: ComponentTypeBuilder.() -> Unit) {
        val builder = ComponentTypeBuilder(internal)
        builder.v3 = true
        builder.apply(init)
        cache += builder.build(id = -1)
    }
}
