plugins {
    id("base-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.guice)
    implementation(projects.api.account)
    implementation(projects.api.config)
    implementation(projects.api.db)
    implementation(projects.api.death)
    implementation(projects.api.equipmentInstance)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.type.typeReferences)
    implementation(projects.engine.plugin)
    implementation(projects.engine.game)
    testImplementation(libs.jupiter.api)
}
