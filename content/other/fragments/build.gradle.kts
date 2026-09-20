plugins {
    id("base-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(projects.api.account)
    implementation(projects.api.cheat)
    implementation(projects.api.config)
    implementation(projects.api.companion)
    implementation(projects.api.death)
    implementation(projects.api.db)
    implementation(projects.api.equipmentInstance)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.random)
    implementation(projects.api.script)
    implementation(projects.engine.game)
    implementation(projects.engine.plugin)
    implementation(kotlin("test"))
}
