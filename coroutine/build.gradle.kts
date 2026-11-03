plugins {
    id("kmongo-dsl.kotlin-library")
}

description = "KMongo DSL extensions for the official MongoDB Kotlin coroutine driver"

dependencies {
    api(project(":kmongo-dsl-core"))
    api(libs.mongodb.driver.kotlin.coroutine)
    api(libs.kotlinx.coroutines.core)
}

// the coroutine extensions are generated from the sync extensions (see GenerateCoroutineSourcesTask)
fun org.litote.kmongo.buildlogic.GenerateCoroutineSourcesTask.configureSources() {
    syncSourceDir.set(rootProject.layout.projectDirectory.dir("sync/src/main/kotlin/org/litote/kmongo"))
    coroutineSourceDir.set(layout.projectDirectory.dir("src/main/kotlin/org/litote/kmongo/coroutine"))
}

tasks.register<org.litote.kmongo.buildlogic.GenerateCoroutineSourcesTask>("generateCoroutineSources") {
    description = "Generates the coroutine extensions from the kmongo-dsl-sync extensions."
    group = "build"
    configureSources()
    verify.set(false)
}

val checkCoroutineSources = tasks.register<org.litote.kmongo.buildlogic.GenerateCoroutineSourcesTask>("checkCoroutineSources") {
    description = "Checks that the coroutine extensions are up-to-date with the kmongo-dsl-sync extensions."
    group = "verification"
    configureSources()
    verify.set(true)
}

tasks.named("check") { dependsOn(checkCoroutineSources) }
