plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(libs.fastutil)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.death)
    implementation(projects.api.equipmentInstance)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.registry)
    implementation(projects.api.script)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.content.pvmPoints)
    implementation(projects.content.interfaces.bank)
    testImplementation(kotlin("test"))
    integrationImplementation(projects.api.death)
    integrationImplementation(projects.api.shops)
}
