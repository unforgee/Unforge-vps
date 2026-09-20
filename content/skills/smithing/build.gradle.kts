plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.equipmentInstance)
    implementation(projects.api.invtx)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.repo)
    integrationImplementation(projects.api.invtx)
    integrationImplementation(projects.api.player)
}
