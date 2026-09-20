package org.rsmod.api.type.builders.interf

import jakarta.inject.Inject
import org.rsmod.api.type.builders.TypeBuilder
import org.rsmod.api.type.builders.resolver.TypeBuilderResolver
import org.rsmod.api.type.builders.resolver.TypeBuilderResult
import org.rsmod.api.type.builders.resolver.TypeBuilderResult.CachePackRequired
import org.rsmod.api.type.builders.resolver.TypeBuilderResult.FullSuccess
import org.rsmod.api.type.builders.resolver.TypeBuilderResult.NameNotFound
import org.rsmod.api.type.builders.resolver.err
import org.rsmod.api.type.builders.resolver.ok
import org.rsmod.api.type.builders.resolver.update
import org.rsmod.api.type.symbols.name.NameMapping
import org.rsmod.game.type.TypeResolver
import org.rsmod.game.type.comp.ComponentTypeBuilder
import org.rsmod.game.type.comp.ComponentTypeList
import org.rsmod.game.type.comp.UnpackedComponentType

public class InterfaceBuilderResolver
@Inject
constructor(private val types: ComponentTypeList, private val nameMapping: NameMapping) :
    TypeBuilderResolver<ComponentTypeBuilder, UnpackedComponentType> {
    private val names: Map<String, Int>
        get() = nameMapping.components

    override fun resolve(
        builders: TypeBuilder<ComponentTypeBuilder, UnpackedComponentType>
    ): List<TypeBuilderResult> = builders.cache.map { it.resolve() }

    private fun UnpackedComponentType.resolve(): TypeBuilderResult {
        val internalId = names[internalName] ?: return err(NameNotFound(internalName))
        val cacheType = types[internalId]

        TypeResolver[this] = internalId

        return if (cacheType != this) {
            update(CachePackRequired)
        } else {
            ok(FullSuccess)
        }
    }
}
