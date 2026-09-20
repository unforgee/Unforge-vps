plugins {
    id("base-conventions")
    application
}

application {
    mainClass.set("org.unforge.bridge.BridgeApplicationKt")
}

dependencies {
    implementation(libs.bundles.logging)
    implementation(libs.jackson.databind)
    implementation(libs.java.websocket)
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
