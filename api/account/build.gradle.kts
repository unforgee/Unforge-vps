plugins {
    id("base-conventions")
    id("integration-test-suite")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.bundles.logging)
    implementation(libs.fastutil)
    implementation(libs.guice)
    implementation(libs.kotlin.coroutines.core)
    implementation(projects.api.db)
    implementation(projects.api.equipmentInstance)
    implementation(projects.api.parsers.jackson)
    implementation(projects.api.parsers.json)
    implementation(projects.api.realm)
    implementation(projects.api.serverConfig)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.module)
    implementation(projects.server.services)

    integrationImplementation(projects.api.db)
    integrationImplementation(projects.api.equipmentInstance)
    integrationImplementation(projects.engine.game)
    integrationImplementation(libs.flyway.core)
    integrationImplementation(projects.api.testing)
}

