plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.pluginCommons)
    integrationImplementation(projects.api.player)
    integrationImplementation(projects.api.invPlugin)
    integrationImplementation(projects.api.equipmentInstance)
}
