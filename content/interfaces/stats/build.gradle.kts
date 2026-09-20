plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.combat.combatWeapon)
    implementation(projects.api.equipmentInstance)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
}
