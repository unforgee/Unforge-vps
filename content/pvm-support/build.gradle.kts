plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.areaChecker)
    implementation(projects.api.equipmentInstance)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)

    testImplementation(kotlin("test"))
}
