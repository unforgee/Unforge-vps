plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.api.playerOutput)
    implementation(projects.api.config)
    implementation(projects.api.realm)
    implementation(projects.content.other.devHub)
    implementation(projects.content.interfaces.settings)
    implementation(projects.content.interfaces.bank)
    implementation(projects.content.interfaces.gameframe)
    implementation(projects.content.skills.smithing)
}
