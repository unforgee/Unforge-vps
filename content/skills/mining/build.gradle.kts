plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.content.skills.skillingCore)
    integrationImplementation(projects.api.player)
    integrationImplementation(projects.content.skills.skillingCore)
}
