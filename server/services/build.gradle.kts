plugins {
    id("base-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(libs.kotlin.coroutines.core)
}
