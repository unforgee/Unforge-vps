plugins {
    id("base-conventions")
    id("integration-test-suite")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.bundles.logging)
    implementation(libs.guice)
    implementation(projects.api.account)
    implementation(projects.api.config)
    implementation(projects.api.db)
    implementation(projects.api.death)
    implementation(projects.api.dbGateway)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.script)
    implementation(projects.api.type.typeBuilders)
    implementation(projects.api.type.typeReferences)
    implementation(projects.api.type.typeScriptDsl)
    implementation(projects.engine.coroutine)
    implementation(projects.engine.events)
    implementation(projects.engine.game)
    implementation(projects.engine.module)
    implementation(projects.engine.plugin)
    testImplementation(libs.flyway.core)
    testImplementation(libs.kotlin.coroutines.core)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(projects.api.realm)
    testImplementation(projects.api.testing.testFactory)
    testImplementation(projects.api.testing.testParams)
}
