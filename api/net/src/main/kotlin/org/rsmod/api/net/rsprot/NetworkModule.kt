package org.rsmod.api.net.rsprot

import com.google.inject.Provider
import com.google.inject.Scopes
import com.google.inject.TypeLiteral
import jakarta.inject.Inject
import java.nio.file.Path
import net.rsprot.protocol.api.NetworkService
import org.openrs2.cache.Store
import org.rsmod.annotations.Js5Cache
import org.rsmod.api.net.rsprot.provider.Js5Store
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule
import org.rsmod.server.services.Service

class NetworkModule : PluginModule() {
    override fun bind() {
        bind(object : TypeLiteral<NetworkService<Player>>() {})
            .toProvider(NetworkServiceProvider::class.java)
            .`in`(Scopes.SINGLETON)
        bindProvider(Js5StoreProvider::class.java)
        addSetBinding<Service>(RspService::class.java)
    }

    private class NetworkServiceProvider @Inject constructor(private val factory: NetworkFactory) :
        Provider<NetworkService<Player>> {
        override fun get(): NetworkService<Player> = factory.build()
    }

    private class Js5StoreProvider @Inject constructor(@Js5Cache private val dir: Path) :
        Provider<Js5Store> {
        override fun get(): Js5Store =
            Store.open(dir).use { store ->
                // Read through a fresh store: the shared js5 store's buffered file channels can
                // hold stale idx255 data when another process/store instance wrote to the cache
                // earlier in this boot (e.g. type-update encoders).
                Js5Store.from(store)
            }
    }
}
