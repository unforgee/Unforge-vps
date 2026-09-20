plugins {
    id("base-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.fastutil)
    implementation(libs.guice)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.config)
    implementation(projects.api.equipmentInstance)
    implementation(projects.api.gameProcess)
    implementation(projects.api.hunt)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.script)
    implementation(projects.api.type.typeReferences)
    implementation(projects.engine.events)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.plugin)
}
