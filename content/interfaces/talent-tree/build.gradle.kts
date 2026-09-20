plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.talents)
    integrationImplementation(projects.api.death)
    integrationImplementation(projects.api.player)
    integrationImplementation(projects.api.talents)
}
