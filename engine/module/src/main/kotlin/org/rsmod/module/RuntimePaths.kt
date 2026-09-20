package org.rsmod.module

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolute
import kotlin.io.path.isDirectory

/**
 * Resolves the installation and writable data roots without relying on the process working
 * directory. The explicit property/environment variable is intended for services and packaged
 * deployments; the distribution layout is detected automatically for Gradle Application builds.
 */
public object RuntimePaths {
    private const val HOME_PROPERTY: String = "unforge.home"
    private const val HOME_ENVIRONMENT: String = "UNFORGE_HOME"
    private const val DATA_PROPERTY: String = "unforge.data"
    private const val DATA_ENVIRONMENT: String = "UNFORGE_DATA"

    public val home: Path by lazy { resolveHome() }

    public val data: Path by lazy {
        explicitPath(DATA_PROPERTY, DATA_ENVIRONMENT)?.let {
            return@lazy it
        }
        home.resolve(".data")
    }

    private fun resolveHome(): Path {
        explicitPath(HOME_PROPERTY, HOME_ENVIRONMENT)?.let {
            return it
        }

        val workingDirectory = Paths.get(System.getProperty("user.dir")).absolute().normalize()
        val distributionRoot = distributionRoot()
        if (distributionRoot != null && distributionRoot.resolve("lib").isDirectory()) {
            return distributionRoot
        }
        return workingDirectory
    }

    private fun distributionRoot(): Path? =
        runCatching {
                val location =
                    Paths.get(RuntimePaths::class.java.protectionDomain.codeSource.location.toURI())
                val libDirectory = if (location.isDirectory()) location else location.parent
                if (libDirectory?.fileName?.toString().equals("lib", ignoreCase = true)) {
                    libDirectory.parent?.absolute()?.normalize()
                } else {
                    null
                }
            }
            .getOrNull()

    private fun explicitPath(property: String, environment: String): Path? {
        val value =
            System.getProperty(property)?.takeUnless(String::isBlank)
                ?: System.getenv(environment)?.takeUnless(String::isBlank)
        return value?.let { Paths.get(it).absolute().normalize() }
    }
}
