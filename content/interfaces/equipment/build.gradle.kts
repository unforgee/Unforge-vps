plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.companion)
    implementation(projects.api.equipmentInstance)
    implementation(projects.api.pluginCommons)
}
