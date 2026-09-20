plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.death)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.talents)

    testImplementation(kotlin("test"))
    integrationImplementation(projects.api.death)
}
