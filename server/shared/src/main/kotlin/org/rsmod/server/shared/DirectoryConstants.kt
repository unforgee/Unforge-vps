package org.rsmod.server.shared

import java.nio.file.Path
import org.rsmod.module.RuntimePaths

object DirectoryConstants {
    val HOME_PATH: Path = RuntimePaths.home
    val DATA_PATH: Path = RuntimePaths.data
    val SYMBOL_PATH: Path = DATA_PATH.resolve("symbols")
    val CACHE_PATH: Path = DATA_PATH.resolve("cache")
}
