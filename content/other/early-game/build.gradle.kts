plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.fastutil)
    implementation(projects.api.account)
    implementation(projects.api.cheat)
    implementation(projects.api.companion)
    implementation(projects.api.config)
    implementation(projects.api.death)
    implementation(projects.api.db)
    implementation(projects.api.invtx)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.repo)
    implementation(projects.api.script)
    implementation(projects.api.type.typeBuilders)
    implementation(projects.engine.events)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.plugin)
    implementation(projects.api.registry)
    implementation(projects.api.shops)
    implementation(projects.api.random)
    implementation(projects.content.other.leagueItems)
    implementation(kotlin("test"))
}
