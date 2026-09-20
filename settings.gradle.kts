import java.nio.file.Files
import java.nio.file.Path

rootProject.name = "rsmod"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.openrs2.org/repository/openrs2-snapshots")
    }
}

include(
    "api",
    "content",
    "engine",
    "server",
    "bridge"
)
if (Files.isDirectory(rootDir.toPath().resolve("weapon-editor"))) {
    include("weapon-editor")
}
if (Files.isDirectory(rootDir.toPath().resolve("tools").resolve("content-editor"))) {
    include("tools:content-editor")
}
if (Files.isDirectory(rootDir.toPath().resolve("tools").resolve("interface-editor"))) {
    include("tools:interface-editor")
}

includeProjects(project(":api"))
includeProjects(project(":content"))
includeProjects(project(":engine"))
includeProjects(project(":server"))

fun includeProjects(pluginProject: ProjectDescriptor) {
    val projectPath = pluginProject.projectDir.toPath()
    Files.walk(projectPath).forEach {
        if (it.fileName.toString() == "build") {
            return@forEach
        }
        if (!Files.isDirectory(it)) {
            return@forEach
        }
        searchProject(pluginProject.name, projectPath, it)
    }
}

fun searchProject(parentName: String, root: Path, currentPath: Path) {
    val hasBuildFile = Files.exists(currentPath.resolve("build.gradle.kts"))
    if (!hasBuildFile) {
        return
    }
    val relativePath = root.relativize(currentPath)
    val projectName = relativePath.toString().replace(File.separator, ":")
    include("$parentName:$projectName")
}
