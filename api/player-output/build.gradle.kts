plugins {
    id("base-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.rsprot.api)
    implementation(projects.api.config)
    implementation(projects.api.equipmentInstance)
    implementation(projects.api.type.typeReferences)
    implementation(projects.engine.game)
    implementation(projects.engine.map)

    testImplementation(kotlin("test"))
    testImplementation(projects.api.testing.testFactory)
}
