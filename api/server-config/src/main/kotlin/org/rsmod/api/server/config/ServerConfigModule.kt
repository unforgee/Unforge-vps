package org.rsmod.api.server.config

import com.google.inject.Provider
import jakarta.inject.Inject
import java.nio.file.Path
import org.rsmod.module.ExtendedModule
import org.rsmod.module.RuntimePaths

public object ServerConfigModule : ExtendedModule() {
    private val configFile: Path
        get() = RuntimePaths.data.resolve("server.toml")

    override fun bind() {
        bindInstance<ServerConfigLoader>()
        bindProvider(ServerConfigProvider::class.java)
    }

    private class ServerConfigProvider @Inject constructor(private val loader: ServerConfigLoader) :
        Provider<ServerConfig> {
        override fun get(): ServerConfig = loader.loadOrCreate(configFile)
    }
}
