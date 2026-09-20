plugins {
    id("base-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.guice)
    implementation(libs.bundles.logging)
    implementation(projects.api.db)
    implementation(projects.api.dbGateway)
    implementation(projects.engine.game)
    implementation(projects.engine.plugin)

    testImplementation(kotlin("test"))
    testImplementation(libs.flyway.core)
    testImplementation(libs.kotlin.coroutines.core)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(projects.server.services)
}