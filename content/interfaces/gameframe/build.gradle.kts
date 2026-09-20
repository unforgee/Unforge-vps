plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.companion)
    implementation(projects.api.equipmentInstance)
    implementation(projects.api.spells)
}
