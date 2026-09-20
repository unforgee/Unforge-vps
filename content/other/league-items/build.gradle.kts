plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.combat.combatEffects)
    implementation(projects.api.combat.combatManager)
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.specials)
    implementation(projects.api.weapons)
    implementation(projects.engine.game)
}
